/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.rdbms.test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthorizationContextService;
import com.vmware.xenon.services.rdbms.NodeGroupChangeManager;
import com.vmware.xenon.services.rdbms.PostgresDocumentIndexService;
import com.vmware.xenon.services.rdbms.PostgresHostUtils;
import com.vmware.xenon.services.rdbms.PostgresServiceHost;

public class PostgresVerificationHost extends VerificationHost {
    private static final Logger logger = Logger.getLogger(PostgresVerificationHost.class.getName());
    private static boolean enablePostgres = true;

    protected DataSource ds;
    protected PostgresDocumentIndexService postgresDocumentIndexService;
    protected boolean enableTemporaryDatabase = true;
    protected String temporaryDatabaseName;
    protected String temporaryPeerDatabaseName;
    protected boolean cleanDbOnStartup = false;
    public String createServiceFactoryLink = "/service";
    private NodeGroupChangeManager nodeGroupChangeManager = new NodeGroupChangeManager(this);
    private boolean isNodeGroupChangeMaintenancePerformed = false;
    private boolean isRejectRemoteRequests = false;

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

    public static VerificationHost create() {
        if (!enablePostgres) {
            return VerificationHost.create();
        }
        return new PostgresVerificationHost();
    }

    public static VerificationHost create(Integer port) throws Exception {
        if (!enablePostgres) {
            return VerificationHost.create(port);
        }
        return create(port, null);
    }

    public static VerificationHost create(Integer port, Consumer<PostgresVerificationHost> handler)
            throws Exception {
        if (!enablePostgres) {
            return VerificationHost.create(port);
        }
        Arguments args = buildDefaultServiceHostArguments(port);
        PostgresVerificationHost h = new PostgresVerificationHost();
        if (handler != null) {
            handler.accept(h);
        }
        initialize(h, args);
        return h;
    }

    public static VerificationHost create(Arguments args)
            throws Exception {
        if (!enablePostgres) {
            return VerificationHost.create(args);
        }
        VerificationHost h = new PostgresVerificationHost();
        initialize(h, args);
        return h;
    }

    public static void setPostgresEnabled(boolean enabled) {
        enablePostgres = enabled;
    }

    public void setCleanDbOnStartup(boolean clean) {
        this.cleanDbOnStartup = clean;
    }

    public String getTemporaryDatabaseName() {
        return this.temporaryDatabaseName;
    }

    public String getTemporaryPeerDatabaseName() {
        return this.temporaryPeerDatabaseName;
    }

    public void setTemporaryDatabaseEnabled(boolean enable) {
        this.enableTemporaryDatabase = enable;
    }

    public void setTemporaryDatabaseName(String temporaryDatabaseName) {
        this.temporaryDatabaseName = temporaryDatabaseName;
    }

    public void setTemporaryPeerDatabaseName(String temporaryPeerDatabaseName) {
        this.temporaryPeerDatabaseName = temporaryPeerDatabaseName;
    }

    protected DataSource getDataSource() {
        if (this.ds == null) {
            Properties props = PostgresHostUtils.getDataSourceProperties();
            props.setProperty("minimumIdle", "0");

            if (this.enableTemporaryDatabase && this.temporaryDatabaseName == null) {
                this.temporaryDatabaseName = TestUtils.getTestDatabaseName();
            }
            if (this.temporaryDatabaseName != null) {
                props.put("dataSource.databaseName", this.temporaryDatabaseName);
            }
            this.ds = PostgresHostUtils.createDataSource(props);
        }
        return this.ds;
    }

    @Override
    protected Service createDefaultDocumentIndexService() {
        if (!enablePostgres) {
            return super.createDefaultDocumentIndexService();
        }

        DataSource ds = getDataSource();

        if (cleanDbOnStartup) {
            try {
                TestUtils.deleteAll(ds);
            } catch (SQLException e) {
                log(Level.SEVERE, "Failed to delete tables in database: %s", e);
                throw new IllegalStateException(e);
            }
        }

        setRemotePersistence(true);
        this.postgresDocumentIndexService = new PostgresDocumentIndexService(this, ds);
        return this.postgresDocumentIndexService;
    }

    @Override
    protected void startDocumentIndexService(Service documentIndexService,
            NodeSelectorService defaultNodeSelectorService) throws Throwable {
        if (!enablePostgres) {
            super.startDocumentIndexService(documentIndexService, defaultNodeSelectorService);
            return;
        }
        PostgresHostUtils.handleStartDocumentIndexService(this, documentIndexService,
                defaultNodeSelectorService, this::startCoreServicesSynchronously);
    }

    @Override
    public void sendRequest(Operation op) {
        if (enablePostgres && PostgresHostUtils.handleSendRequest(this, op, this::findService)) {
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
        if (enablePostgres) {
            PostgresHostUtils
                    .handleServiceStart(this, post, service, this.postgresDocumentIndexService);
        }
        return super.startService(post, service, onDemandTriggeringOp);
    }

    @Override
    public void replicateRequest(EnumSet<Service.ServiceOption> serviceOptions,
            ServiceDocument state,
            String selectorPath, String selectionKey, Operation op) {
        if (enablePostgres && PostgresHostUtils
                .handleReplicateRequest(this, serviceOptions, state, selectorPath,
                        selectionKey, op)) {
            return;
        }
        super.replicateRequest(serviceOptions, state, selectorPath, selectionKey, op);
    }


    @Override
    public void registerForServiceAvailability(Operation.CompletionHandler completion,
            String nodeSelectorPath, boolean checkReplica, String... servicePaths) {
        log("servicePaths %s: %s", checkReplica, Utils.toJson(servicePaths));
        if (enablePostgres && checkReplica) {
            PostgresHostUtils.registerForReplicatedServiceAvailability(this, completion,
                    isStarted() ? getSystemAuthorizationContext() : null, nodeSelectorPath,
                    servicePaths);
            return;
        }
        super.registerForServiceAvailability(completion, nodeSelectorPath, checkReplica, servicePaths);
    }

    @Override
    public ServiceHost start() throws Throwable {
        if (!enablePostgres) {
            return super.start();
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
    public void stop() {
        super.stop();
        if (!enablePostgres) {
            return;
        }

        // Evict connection pool, important for node group testing to make sure we don't have
        // too many open connections.
        PostgresHostUtils.closeDataSource(this.ds);
        this.ds = null;

        if (this.enableTemporaryDatabase) {
            TestUtils.decreaseTestDatabaseRefCount(this.temporaryDatabaseName);
            this.temporaryDatabaseName = null;

            TestUtils.decreaseTestDatabaseRefCount(this.temporaryPeerDatabaseName);
            this.temporaryPeerDatabaseName = null;
        }
    }

    @Override
    public VerificationHost setUpLocalPeerHost(int port, long maintIntervalMicros,
            Collection<ServiceHost> hosts, String location) throws Throwable {
        if (!enablePostgres) {
            return super.setUpLocalPeerHost(port, maintIntervalMicros, hosts, location);
        }

        // It seems setUpLocalPeerHost can be called in parallel
        synchronized (this) {
            if (this.enableTemporaryDatabase && this.temporaryPeerDatabaseName == null) {
                this.temporaryPeerDatabaseName = TestUtils.getTestDatabaseName();
            }
        }

        VerificationHost h = create(port, host -> {
            if (this.temporaryPeerDatabaseName != null) {
                host.enableTemporaryDatabase = false;
                host.temporaryDatabaseName = this.temporaryPeerDatabaseName;
            }
        });

        h.setPeerSynchronizationEnabled(this.isPeerSynchronizationEnabled());
        h.setAuthorizationEnabled(this.isAuthorizationEnabled());
        if (this.getCurrentHttpScheme() == HttpScheme.HTTPS_ONLY) {
            h.setPort(-1);
            h.setSecurePort(0);
        }

        if (this.isAuthorizationEnabled()) {
            h.setAuthorizationService(new AuthorizationContextService());
        }

        try {
            createAndAttachSSLClient(h);
            h.setCertificateFileReference(this.getState().certificateFileReference);
            h.setPrivateKeyFileReference(this.getState().privateKeyFileReference);
            h.setPrivateKeyPassphrase(this.getState().privateKeyPassphrase);
            if (location != null) {
                h.setLocation(location);
            }

            h.start();
            h.setMaintenanceIntervalMicros(maintIntervalMicros);
        } catch (Throwable var8) {
            throw new Exception(var8);
        }

        this.addPeerNode(h);
        if (hosts != null) {
            hosts.add(h);
        }

        this.completeIteration();
        return h;
    }

    /**
     * This is needed for photon-model testing to make sure reflection is working.
     */
    @Override
    public void addPrivilegedService(Class<? extends Service> serviceType) {
        super.addPrivilegedService(serviceType);
    }

    @Override
    public Operation createServiceStartPost(TestContext ctx) {
        if (!enablePostgres) {
            return super.createServiceStartPost(ctx);
        }

        Operation post = Operation.createPost(null);
        post.setUri(UriUtils.buildUri(this, createServiceFactoryLink + "/" + post.getId()));
        return post.setCompletion(ctx.getCompletion());
    }

    @Override
    public void handleNodeGroupChangeMaintenance(String nodeSelectorPath) {
        if (!enablePostgres) {
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

        this.isNodeGroupChangeMaintenancePerformed = true;
    }

    public static long restartHost(VerificationHost host) throws Throwable {
        if (!(host instanceof PostgresVerificationHost)) {
            long beforeRestart = Utils.getNowMicrosUtc();
            VerificationHost.restartStatefulHost(host, true);
            return beforeRestart;
        }

        PostgresVerificationHost h = (PostgresVerificationHost) host;

        // Get database names, add ref count to make sure databased is not stolen
        String temporaryDatabaseName = h.getTemporaryDatabaseName();
        String temporaryPeerDatabaseName = h.getTemporaryPeerDatabaseName();

        TestUtils.increaseTestDatabaseRefCount(temporaryDatabaseName);
        TestUtils.increaseTestDatabaseRefCount(temporaryPeerDatabaseName);
        h.stop();

        long beforeRestart = Utils.getNowMicrosUtc();

        // Reuse database names, and decrease ref count
        h.setTemporaryDatabaseName(temporaryDatabaseName);
        h.setTemporaryPeerDatabaseName(temporaryPeerDatabaseName);

        // Re-register document index service
        Service documentIndexService = h.createDefaultDocumentIndexService();
        h.setDocumentIndexingService(documentIndexService);

        logger.info("Restarting host");
        h.start();

        // TestUtils.decreaseTestDatabaseRefCount(temporaryDatabaseName);
        // TestUtils.decreaseTestDatabaseRefCount(temporaryPeerDatabaseName);

        return beforeRestart;
    }

    public boolean isNodeGroupChangeMaintenancePerformed() {
        return this.isNodeGroupChangeMaintenancePerformed;
    }
}
