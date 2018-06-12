/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.services.rdbms;

import java.util.EnumSet;
import java.util.logging.Level;

import javax.sql.DataSource;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.CheckpointFactoryService;
import com.vmware.xenon.services.common.CheckpointService;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.TenantService;
import com.vmware.xenon.services.common.TransactionFactoryService;
import com.vmware.xenon.services.common.TransactionService;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService;

public abstract class PostgresServiceHost extends ServiceHost {
    public static final String DEFAULT_LATEST_SNAPSHOT_RESOURCE_PATH =
            "liquibase/latest-snapshot.json";
    public static final String DEFAULT_CHANGELOG_RESOURCE_PATH = "liquibase/changelog.xml";

    protected boolean enablePostgres = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enablePostgres",
            true
    );
    protected boolean enableRegisterPostgresSchema = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enableRegisterPostgresSchema",
            false
    );
    protected boolean enableValidateLiquibaseSnapshot = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enableValidateLiquibaseSnapshot",
            false
    );
    protected boolean enableLiquibaseUpdate = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enableLiquibaseUpdate",
            false
    );
    protected boolean createTableOnFactoryStart = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "createTableOnFactoryStart",
            true
    );
    protected boolean enablePeriodicCheck = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enablePeriodicCheck",
            true
    );
    protected boolean enableClearCacheOnNodeGroupChange = XenonConfiguration.bool(
            PostgresServiceHost.class,
            "enableClearCacheOnNodeGroupChange",
            true
    );

    private DataSource ds;
    private PostgresDocumentIndexService postgresDocumentIndexService;
    private NodeGroupChangeManager nodeGroupChangeManager = new NodeGroupChangeManager(this);
    private boolean isRejectRemoteRequests = false;

    protected String latestSnapshotResourcePath = DEFAULT_LATEST_SNAPSHOT_RESOURCE_PATH;
    protected String changelogResourcePath = DEFAULT_CHANGELOG_RESOURCE_PATH;

    protected PostgresServiceHost() {
    }

    public void enableLiquibase() {
        // TODO: Temporary workaround until this is the default behavior
        this.enableValidateLiquibaseSnapshot = true;
        this.enableRegisterPostgresSchema = true;
        this.enableLiquibaseUpdate = true;
        this.createTableOnFactoryStart = false;
    }

    PostgresSchemaManager getPostgresSchemaManager() {
        return this.postgresDocumentIndexService.getDao().getPostgresSchemaManager();
    }

    protected DataSource getDataSource() {
        if (this.ds == null) {
            this.ds = PostgresHostUtils.createDataSource();
        }
        return this.ds;
    }

    /***
     * Override to register factory services used by the host.
     */
    protected void registerPostgresSchema(PostgresSchemaManager sm) {
        sm.addFactory(CheckpointFactoryService.SELF_LINK, CheckpointService.class);
        sm.addTable(td -> {
            td.useStatefulService(AuthCredentialsService.class);
            td.setIndexType("privateKey", "hash");
            td.setIndexType("publicKey", "hash");
        });
        sm.addFactory(RoleService.class);
        sm.addFactory(TenantService.class);
        sm.addFactory(UserService.class);
        sm.addFactory(UserGroupService.class);
        sm.addFactory(ResourceGroupService.class);
        sm.addFactory(TransactionFactoryService.SELF_LINK, TransactionService.class);
    }

    @Override
    public ServiceHost start() throws Throwable {
        if (!this.enablePostgres) {
            return super.start();
        }

        if (enableRegisterPostgresSchema) {
            // Allow host to register schema
            registerPostgresSchema(getPostgresSchemaManager());
        }

        if (enableRegisterPostgresSchema && enableValidateLiquibaseSnapshot) {
            log(Level.INFO, "Validating Liquibase snapshot");
            boolean valid;
            try {
                valid = validateLiquibaseSnapshot(getPostgresSchemaManager());
            } catch (Throwable e) {
                log(Level.SEVERE, "Failed validating liquibase snapshot: %s", Utils.toString(e));
                throw new AssertionError(e);
            }
            if (!valid) {
                throw new AssertionError("Need to update to latest Liquibase snapshot");
            }
        }

        if (enableLiquibaseUpdate) {
            log(Level.INFO, "Starting Liquibase upgrade");
            try {
                liquibaseUpdate(getDataSource());
            } catch (Exception e) {
                log(Level.SEVERE, "Failed Liquibase update: %s", Utils.toString(e));
                throw new AssertionError(e);
            }
            log(Level.INFO, "Successful Liquibase update");
        }

        // disable synchronization
        setPeerSynchronizationEnabled(false);

        super.start();

        if (this.enableClearCacheOnNodeGroupChange) {
            // Use system context
            OperationContext origContext = OperationContext.getOperationContext();
            setAuthorizationContext(getSystemAuthorizationContext());

            // Subscribe for node group change and clear cache on changes
            PostgresHostUtils.clearCacheOnNodeGroupChange(this);

            // Restore context
            OperationContext.setFrom(origContext);
        }

        return this;
    }

    @Override
    protected Service createDefaultDocumentIndexService() {
        if (!this.enablePostgres) {
            return super.createDefaultDocumentIndexService();
        }

        setRemotePersistence(true);
        this.postgresDocumentIndexService = new PostgresDocumentIndexService(this,
                getDataSource());
        return this.postgresDocumentIndexService;
    }

    protected boolean validateLiquibaseSnapshot(PostgresSchemaManager schemaManager) throws Exception {
        return PostgresLiquibaseUtils.validateSnapshot(schemaManager.getSnapshot(),
                this.latestSnapshotResourcePath, true);
    }

    protected void liquibaseUpdate(DataSource ds) throws Exception {
        PostgresLiquibaseUtils.update(ds, this.changelogResourcePath);
    }

    @Override
    protected void startDocumentIndexService(Service documentIndexService,
            NodeSelectorService defaultNodeSelectorService) throws Throwable {
        if (!this.enablePostgres) {
            super.startDocumentIndexService(documentIndexService, defaultNodeSelectorService);
            return;
        }

        PostgresHostUtils.handleStartDocumentIndexService(this, documentIndexService,
                defaultNodeSelectorService, this::startCoreServicesSynchronously);
    }

    @Override
    public void sendRequest(Operation op) {
        if (this.enablePostgres &&
                PostgresHostUtils.handleSendRequest(this, op, this::findService)) {
            return;
        }
        super.sendRequest(op);
    }

    public boolean isRejectRemoteRequests() {
        return this.isRejectRemoteRequests;
    }

    public void setRejectRemoteRequests(boolean rejectRemoteRequests) {
        this.isRejectRemoteRequests = rejectRemoteRequests;
    }

    @Override
    public boolean handleRequest(Service service, Operation inboundOp) {
        if (inboundOp == null && service != null) {
            inboundOp = service.dequeueRequest();
        }

        if (inboundOp == null) {
            return true;
        }

        // Reject remote requests with 503 if node is not started, node stopping or
        // isRejectRemoteRequests flag is true (which can be used to reject requests before starting
        // factories)
        if ((!isStarted() || isStopping() || this.isRejectRemoteRequests) && inboundOp.isRemote()) {
            PostgresHostUtils.failServerNotAvailable(inboundOp);
            return true;
        }

        return super.handleRequest(null, inboundOp);
    }

    @Override
    protected ServiceHost startService(Operation post, Service service,
            Operation onDemandTriggeringOp) {
        if (this.enablePostgres) {
            boolean registerFactory = !enableRegisterPostgresSchema || createTableOnFactoryStart;
            PostgresHostUtils
                    .handleServiceStart(this, post, service, this.postgresDocumentIndexService,
                            registerFactory);
        }
        return super.startService(post, service, onDemandTriggeringOp);
    }

    @Override
    public void replicateRequest(EnumSet<Service.ServiceOption> serviceOptions, ServiceDocument state,
            String selectorPath, String selectionKey, Operation op) {
        if (this.enablePostgres && PostgresHostUtils.handleReplicateRequest(this, serviceOptions,
                state, selectorPath, selectionKey, op)) {
            return;
        }
        super.replicateRequest(serviceOptions, state, selectorPath, selectionKey, op);
    }

    @Override
    public void registerForServiceAvailability(Operation.CompletionHandler completion,
            String nodeSelectorPath, boolean checkReplica, String... servicePaths) {
        if (this.enablePostgres && checkReplica) {
            PostgresHostUtils.registerForReplicatedServiceAvailability(this, completion,
                    isStarted() ? getSystemAuthorizationContext() : null, nodeSelectorPath,
                    servicePaths);
            return;
        }
        super.registerForServiceAvailability(completion, nodeSelectorPath, checkReplica, servicePaths);
    }

    @Override
    public void stop() {
        super.stop();

        if (this.enablePostgres) {
            PostgresHostUtils.closeDataSource(this.ds);
        }
    }

    @Override
    public void handleNodeGroupChangeMaintenance(String nodeSelectorPath) {
        if (!this.enablePostgres) {
            super.handleNodeGroupChangeMaintenance(nodeSelectorPath);
            return;
        }

        if (!this.enablePeriodicCheck) {
            return;
        }

        // find all factory services and schedule node group change event
        Operation.CompletionHandler ch = (op, ex) -> {
            if (ex != null) {
                log(Level.WARNING, "Failed to retrieve factory paths: %s", ex);
                return;
            }
            ServiceDocumentQueryResult result = op.getBody(ServiceDocumentQueryResult.class);
            for (String factoryPath : result.documentLinks) {
                Service service = this.findService(factoryPath);

                if (service == null) {
                    log(Level.WARNING, "Failed to retrieve factory service '%s'",
                            factoryPath);
                    continue;
                }

                FactoryService factoryService = (FactoryService) service;

                String serviceSelectorPath = factoryService.getPeerNodeSelectorPath();
                if (!nodeSelectorPath.equals(serviceSelectorPath)) {
                    continue;
                }

                if (!factoryService.hasChildOption(Service.ServiceOption.PERIODIC_MAINTENANCE)) {
                    continue;
                }

                this.run(() -> {
                    setAuthorizationContext(getSystemAuthorizationContext());
                    this.nodeGroupChangeManager
                            .handleNodeGroupChangeForPersistencePeriodicFactory(nodeSelectorPath,
                                    factoryService);
                });
            }
        };

        // retrieve persistence service factory paths
        EnumSet<Service.ServiceOption> options = EnumSet.of(Service.ServiceOption.FACTORY,
                Service.ServiceOption.PERSISTENCE);
        Operation dummyOp = Operation.createGet(null).setCompletion(ch);
        queryServiceUris(options, true, dummyOp);
    }

}
