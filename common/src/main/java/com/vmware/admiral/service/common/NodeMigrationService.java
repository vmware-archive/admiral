/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.net.URI;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.MigrationTaskService;
import com.vmware.xenon.services.common.MigrationTaskService.State;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Service is meant to do a migration of the documents/states from another xenon node.
 */
public class NodeMigrationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.MIGRATION;

    private static final int MIGRATION_CHECK_DELAY_SECONDS = 6;
    private static final int MIGRATION_CHECK_RETRIES = 200;

    public Set<String> services = ConcurrentHashMap.newKeySet();

    // Services that must be migrated last because their states depend on others
    private Set<String> dependentServices = ConcurrentHashMap.newKeySet();

    public static class MigrationRequest {
        public String sourceNodeGroup;
        public String destinationNodeGroup;
    }

    @Override
    public void handlePost(Operation post) {
        MigrationRequest body = post.getBody(MigrationRequest.class);
        if (services == null || services.isEmpty()) {
            getHost().log(Level.INFO, "No registered services for migration found!");
            post.complete();
            return;
        } else if (body.sourceNodeGroup == null || body.sourceNodeGroup.isEmpty()) {
            getHost().log(Level.INFO, "Source is empty!");
            post.complete();
            return;
        }
        setDependentServices();
        migrateData(body, post);
    }

    @Override
    public void handlePatch(Operation patch) {
        NodeMigrationService patchState = patch.getBody(NodeMigrationService.class);
        if (patchState.services != null && !patchState.services.isEmpty()) {
            services.addAll(patchState.services);
        }

        // Add the factories that are not started from admiral
        services.add("/core/auth/credentials");
        services.add("/resources/pools");
        services.add("/resources/groups");
        services.add("/resources/tags");

        // Do not migrate these services
        services.remove("/resources/host-container-list-data-collection");
        services.remove("/resources/container-control-loop");
        services.remove("/resources/hosts-data-collections");
        services.remove("/config/event-topic");

        patch.complete();
    }

    private void setDependentServices() {
        // elastic placement zones depend on resource pools
        services.remove(ManagementUriParts.ELASTIC_PLACEMENT_ZONES);
        // compute states should be migrated after containers to avoid discovered containers
        services.remove(ComputeService.FACTORY_LINK);
        dependentServices.add(ManagementUriParts.ELASTIC_PLACEMENT_ZONES);
        dependentServices.add(ComputeService.FACTORY_LINK);
    }

    private void migrateData(MigrationRequest body, Operation post) {
        State migrationState = new State();
        migrationState.continuousMigration = false;
        try {
            migrationState.sourceNodeGroupReference = new URI(body.sourceNodeGroup);
        } catch (Exception e) {
            getHost().log(Level.SEVERE, "Invalid sourceNodeGroupReference", e.getMessage());
            post.fail(e);
            return;
        }
        if (body.destinationNodeGroup == null || body.destinationNodeGroup.isEmpty()) {
            try {
                migrationState.destinationNodeGroupReference = new URI(
                        getHost().getPublicUriAsString() + ServiceUriPaths.DEFAULT_NODE_GROUP);
            } catch (Exception e) {
                getHost().log(Level.SEVERE, "Invalid destinationNodeGroupReference",
                        e.getMessage());
                post.fail(e);
                return;
            }
        } else {
            try {
                migrationState.destinationNodeGroupReference = new URI(body.destinationNodeGroup);
            } catch (Exception e) {
                getHost().log(Level.SEVERE, "Invalid destinationNodeGroupReference",
                        e.getMessage());
                post.fail(e);
                return;
            }
        }

        performMigration(post, services, migrationState, () -> {
            performMigration(post, dependentServices, migrationState, () -> {
                logInfo("Migration completed successfully");
                post.complete();
            });
        });
    }

    private void performMigration(Operation post, Set<String> services, State migrationState,
            Runnable callback) {

        Set<String> migrationTasksInProgress = ConcurrentHashMap.newKeySet();
        AtomicBoolean hasError = new AtomicBoolean(false);

        Iterator<String> servicesIterator = services.iterator();
        servicesIterator.forEachRemaining(currentService -> {
            migrationState.destinationFactoryLink = currentService;
            migrationState.sourceFactoryLink = currentService;

            Operation operation = Operation.createPost(this, MigrationTaskService.FACTORY_LINK)
                    .setBody(migrationState)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            getHost().log(Level.SEVERE,
                                    "Failure when calling migration task. Error: %s",
                                    ex.getMessage());
                            if (hasError.compareAndSet(false, true)) {
                                post.fail(new Throwable("Failure when calling migration task"));
                            }
                        } else {
                            State state = o.getBody(State.class);
                            migrationTasksInProgress.add(state.documentSelfLink);
                            getHost().log(Level.INFO, "Migration task created: %s",
                                    state.documentSelfLink);
                            if (migrationTasksInProgress.size() == services.size()) {
                                waitForMigrationToComplete(MIGRATION_CHECK_RETRIES,
                                        migrationTasksInProgress, hasError, post, callback);
                            }
                        }
                    });
            super.setAuthorizationContext(operation, this.getSystemAuthorizationContext());
            sendRequest(operation);
        });
    }

    private void waitForMigrationToComplete(int retryCount, Set<String> migrationTasksInProgress,
            AtomicBoolean hasError, Operation post, Runnable callback) {

        getHost().schedule(() -> {
            Iterator<String> tasksIterator = migrationTasksInProgress.iterator();
            tasksIterator.forEachRemaining(currentTask -> {
                sendRequest(Operation.createGet(this, currentTask)
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                getHost().log(Level.SEVERE,
                                        "Failure getting migration task: %s. Error: %s",
                                        currentTask, ex.getMessage());
                            } else {
                                State state = o.getBody(State.class);
                                if (state.taskInfo.stage == TaskStage.FINISHED) {
                                    logInfo("Migration task completed: %s", currentTask);
                                    migrationTasksInProgress.remove(currentTask);
                                } else if (state.taskInfo.stage == TaskStage.FAILED) {
                                    // If a factory is missing on the source this is not a problem
                                    // for the migration. The factory should be skipped.
                                    if (state.taskInfo.failure.message.contains(
                                            String.valueOf(Operation.STATUS_CODE_NOT_FOUND))) {
                                        logInfo("Migration task skipped because it does not exist on source: %s",
                                                currentTask);
                                        migrationTasksInProgress.remove(currentTask);
                                    } else {
                                        logInfo("Migration task failed: %s", currentTask);
                                        if (hasError.compareAndSet(false, true)) {
                                            logSevere("Migration failed");
                                            post.fail(new Throwable(
                                                    "One or more migration tasks failed"));
                                            migrationTasksInProgress.remove(currentTask);
                                        }
                                    }
                                }
                            }
                        }));
            });
            if (migrationTasksInProgress.isEmpty() && !hasError.get()) {
                callback.run();
                return;
            }
            if (retryCount > 0) {
                waitForMigrationToComplete(retryCount - 1, migrationTasksInProgress, hasError,
                        post, callback);
            } else if (!hasError.get()) {
                post.fail(new Throwable("Migration did not finish in the expected time frame"));
                logSevere("Migration did not finish in the expected time frame");
            }
        }, MIGRATION_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
