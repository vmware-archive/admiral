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
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.MigrationTaskService;
import com.vmware.xenon.services.common.MigrationTaskService.State;

/**
 * Service is meant to do a migration of the documents/states from another xenon node.
 */
public class NodeMigrationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.MIGRATION;

    private static final String DEFAULT_NODE_GROUP = "/core/node-groups/default";
    private static final int MIGRATION_CHECK_DELAY_SECONDS = 6;
    private static final int MIGRATION_CHECK_RETRIES = 100;

    public Set<String> services = ConcurrentHashMap.newKeySet();

    private Set<String> migrationTasksInProgress;
    private boolean failure;

    public static class MigrationRequest {
        public String sourceNodeGroup;
        public String destinationNodeGroup;
    }

    @Override
    public void handlePost(Operation post) {
        migrationTasksInProgress = ConcurrentHashMap.newKeySet();
        failure = false;
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
        services.add("/resources/compute");
        services.add("/resources/pools");

        // TODO Check why the migration fails for those services
        services.remove("/resources/host-container-list-data-collection");
        services.remove("/resources/hosts-data-collections");
        services.remove("/resources/group-placements");

        patch.complete();
    }

    private void migrateData(MigrationRequest body, Operation post) {
        Iterator<String> servicesIterator = services.iterator();
        State migrationState = new State();
        try {
            migrationState.sourceNodeGroupReference = new URI(body.sourceNodeGroup);
        } catch (Exception e) {
            getHost().log(Level.SEVERE, "Invalid sourceNodeGroupReference", e.getMessage());
            post.fail(e);
        }
        if (body.destinationNodeGroup == null || body.destinationNodeGroup.isEmpty()) {
            try {
                migrationState.destinationNodeGroupReference = new URI(
                        getHost().getPublicUriAsString() + DEFAULT_NODE_GROUP);
            } catch (Exception e) {
                getHost().log(Level.SEVERE, "Invalid destinationNodeGroupReference",
                        e.getMessage());
                post.fail(e);
            }
        } else {
            try {
                migrationState.destinationNodeGroupReference = new URI(body.destinationNodeGroup);
            } catch (Exception e) {
                getHost().log(Level.SEVERE, "Invalid destinationNodeGroupReference",
                        e.getMessage());
                post.fail(e);
            }
        }

        servicesIterator.forEachRemaining(currentService -> {
            migrationState.destinationFactoryLink = currentService;
            migrationState.sourceFactoryLink = currentService;

            migrationState.continuousMigration = false;

            sendRequest(Operation.createPost(this, MigrationTaskService.FACTORY_LINK)
                    .setBody(migrationState)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            getHost().log(Level.SEVERE,
                                    "Failure when calling migration task. Error: %s",
                                    ex.getMessage());
                        } else {
                            State state = o.getBody(State.class);
                            migrationTasksInProgress.add(state.documentSelfLink);
                            getHost().log(Level.INFO, "Migration task created: %s",
                                    state.documentSelfLink);
                            if (migrationTasksInProgress.size() == services.size()) {
                                getHost().log(Level.INFO, "All migration tasks created.");
                                waitForMigrationToComplete(MIGRATION_CHECK_RETRIES,
                                        migrationTasksInProgress, post);
                            }
                        }
                    }));
        });
    }

    private void waitForMigrationToComplete(int retryCount, Set<String> migrationTasksInProgress,
            Operation post) {
        getHost().schedule(
                () -> {
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
                                            logInfo("Migration task failed: %s", currentTask);
                                            migrationTasksInProgress.remove(currentTask);
                                            failure = true;
                                        }
                                    }
                                }));
                    });
                    int retriesRemaining = retryCount - 1;
                    if (migrationTasksInProgress.isEmpty() && !failure) {
                        logInfo("Migration completed successfully");
                        post.complete();
                        return;
                    } else if (migrationTasksInProgress.isEmpty() && failure) {
                        logSevere("Migration failed");
                        post.fail(
                                new Throwable(String.format("One or more migration tasks failed")));
                        return;
                    }
                    if (retryCount > 0) {
                        waitForMigrationToComplete(retriesRemaining, migrationTasksInProgress,
                                post);
                    } else {
                        post.fail(new Throwable(String
                                .format("Migration did not finish in the expected time frame")));
                        logSevere("Migration did not finish in the expected time frame");
                    }
                }, MIGRATION_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
