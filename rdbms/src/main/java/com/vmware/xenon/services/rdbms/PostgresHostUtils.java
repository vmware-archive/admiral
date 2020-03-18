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

package com.vmware.xenon.services.rdbms;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.SynchronizationTaskService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.services.common.AuthorizationTokenCacheService;
import com.vmware.xenon.services.common.AuthorizationTokenCacheService.AuthorizationTokenCacheServiceState;
import com.vmware.xenon.services.common.GraphQueryTaskService;
import com.vmware.xenon.services.common.LocalQueryTaskFactoryService;
import com.vmware.xenon.services.common.NodeGroupUtils;
import com.vmware.xenon.services.common.NodeSelectorSynchronizationService.SynchronizePeersRequest;
import com.vmware.xenon.services.common.QueryPageForwardingService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTaskFactoryService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.UserService;

public class PostgresHostUtils {

    private static final Logger logger = Logger.getLogger(PostgresHostUtils.class.getName());

    public static final String POSTGRES_SERVER = System.getProperty("postgres.server",
            "localhost");
    public static final String POSTGRES_PORT = System.getProperty("postgres.port",
            "5432");
    public static final String POSTGRES_DB = System.getProperty("postgres.db",
            "test");
    public static final String POSTGRES_USER = System.getProperty("postgres.user",
            "postgres");
    public static final String POSTGRES_PASSWORD = System.getProperty("postgres.password",
            "password");
    public static final String POSTGRES_SSL = System.getProperty("postgres.ssl",
            "false");

    private static final String HIKARI_PROPERTY_NAME_PREFIX = "hikari.";
    private static final String PRAGMA_DIRECTIVE_FROM_AUTH_BROADCAST = "xn-from-auth-broadcast";

    private static final boolean DISABLE_SYNCHRONIZE_FACTORIES = XenonConfiguration.bool(
            PostgresHostUtils.class,
            "disableSynchronizeFactories",
            false
    );

    private static final boolean DISABLE_SYNCHRONIZE_PEERS_REQUEST = XenonConfiguration.bool(
            PostgresHostUtils.class,
            "disableSynchronizePeersRequest",
            false
    );

    static final boolean REGISTER_MBEANS = XenonConfiguration.bool(
            PostgresHostUtils.class,
            "registerMbeans",
            true
    );

    private static final long NODE_GROUP_UTILS_OPERATION_TIMEOUT_SECONDS = Long.getLong(
            NodeGroupUtils.PROPERTY_NAME_OPERATION_TIMEOUT_SECONDS,
            TimeUnit.MICROSECONDS.toSeconds(
                    ServiceHost.ServiceHostState.DEFAULT_OPERATION_TIMEOUT_MICROS / 3));

    static {
        // Disable SynchronizationTaskService checkpoint
        System.setProperty("xenon.SynchronizationTaskService.isCheckpointEnabled", "false");

        // Avoid xenon request retry for Timeout exceptions
        System.setProperty("xenon.ServiceHost.inspectForwardingRetry", "true");

        Utils.registerKind(UserService.UserState.class, Utils.buildKind(UserService.UserState.class));
        System.setProperty("xenon.StatefulService.sequentialIndexing", "true");
    }

    private PostgresHostUtils() {
    }

    public static Properties getDataSourceProperties() {
        // TODO: Load from properties file or env variables
        Properties props = new Properties();
        props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
        props.setProperty("dataSource.user", POSTGRES_USER);
        props.setProperty("dataSource.password", POSTGRES_PASSWORD);
        props.setProperty("dataSource.databaseName", POSTGRES_DB);
        props.setProperty("dataSource.serverName", POSTGRES_SERVER);
        props.setProperty("dataSource.portNumber", POSTGRES_PORT);
        props.setProperty("dataSource.ssl", POSTGRES_SSL);
        props.setProperty("maximumPoolSize", "10");
        props.setProperty("registerMbeans", Boolean.toString(REGISTER_MBEANS));

        // Allow setting Hikari config from system property by using "hikari." prefix
        // for ex, java -Dhikari.maximumPoolSize=20 ...
        Properties systemProperties = System.getProperties();
        System.getProperties().keySet().forEach(k -> {
            String keyStr = String.valueOf(k);
            if (keyStr.startsWith(HIKARI_PROPERTY_NAME_PREFIX)) {
                keyStr = keyStr.substring(HIKARI_PROPERTY_NAME_PREFIX.length());
                String value = systemProperties.getProperty(String.valueOf(k));
                props.setProperty(keyStr, value);
            }
        });

        return props;
    }

    public static DataSource createDataSource() {
        Properties props = getDataSourceProperties();
        return createDataSource(props);
    }

    public static DataSource createDataSource(Properties props) {
        HikariConfig config = new HikariConfig(props);
        HikariDataSource ds = new HikariDataSource(config);

        Properties logProps = new Properties();
        logProps.putAll(props);
        logProps.remove("dataSource.password");
        logger.info("Created new DataSource: " + ds + ", " + logProps);
        return ds;
    }

    public static void closeDataSource(DataSource ds) {
        logger.info("Closing DataSource: " + ds);

        // Close connection pool, important for node group testing to make sure we don't have
        // too many open connections.
        if (ds instanceof Closeable) {
            try {
                ((Closeable) ds).close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public static List<String> getDocumentTableNames(DataSource ds) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE' AND table_name LIKE 'docs_%'")) {
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    tables.add(tableName);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get document table names: " + e);
            throw new IllegalStateException(e);
        }
        return tables;
    }

    public static void deleteAll(DataSource ds) throws SQLException {
        logger.info("Deleting all rows from existing tables");
        List<String> tables = PostgresHostUtils.getDocumentTableNames(ds);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String tableName : tables) {
                stmt.executeUpdate("DELETE FROM " + tableName);
            }
        } catch (SQLException e) {
            logger.severe("Failed to delete all tables: " + e);
            throw e;
        }
    }

    public static void dropAll(DataSource ds) throws SQLException {
        logger.info("Dropping all tables");
        List<String> tables = PostgresHostUtils.getDocumentTableNames(ds);
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            for (String tableName : tables) {
                stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            }

            // liquibase tables
            stmt.executeUpdate("DROP TABLE IF EXISTS databasechangelog");
            stmt.executeUpdate("DROP TABLE IF EXISTS databasechangeloglock");
        } catch (SQLException e) {
            logger.severe("Failed to drop all tables: " + e);
            throw e;
        }
    }

    public static void handleServiceStart(ServiceHost host, Operation op, Service service,
            PostgresDocumentIndexService indexService) {
        handleServiceStart(host, op, service, indexService, true);
    }

    public static void handleServiceStart(ServiceHost host, Operation op, Service service,
            PostgresDocumentIndexService indexService, boolean registerFactory) {
        // only interested in factories
        if (!(service instanceof FactoryService)) {
            return;
        }

        op.nestCompletion((o, e) -> {
            if (e != null) {
                op.fail(e);
                return;
            }

            // exclude non-persistent services
            FactoryService factoryService = (FactoryService) service;
            if (!factoryService.hasChildOption(ServiceOption.PERSISTENCE) &&
                    !service.hasOption(ServiceOption.PERSISTENCE)) {
                op.complete();
                return;
            }

            // exclude persistent services that use a different document index service
            if (!isPostgresDocumentIndexForService(factoryService, host)) {
                host.log(Level.INFO, "Ignoring %s service as it does not use Postgres document "
                        + "index", factoryService.getStateType().getCanonicalName());
                op.complete();
                return;
            }

            // set Factory as Available since we disabled synchronization
            // TODO: We should avoid this hack, if we can call setAvailable() elsewhere
            if (!factoryService.hasChildOption(ServiceOption.PERIODIC_MAINTENANCE)) {
                factoryService.setAvailable(true);
            }

            if (!registerFactory) {
                op.complete();
                return;
            }

            // TODO: Remove after using liquibase to create tables

            // initialize DAO layer for this document type
            String childLink = UriUtils
                    .buildUriPath(factoryService.getSelfLink(), "child-template");

            ServiceDocument childTemplate = (ServiceDocument) ((ServiceDocumentQueryResult) factoryService
                    .getDocumentTemplate()).documents.get(childLink);

            Class<? extends ServiceDocument> stateType = factoryService.getStateType();
            host.log(Level.INFO, "Initializing Postgres DB for %s under %s",
                    stateType.getCanonicalName(), factoryService.getSelfLink());

            try {
                indexService.getDao().initForDocument(factoryService.getSelfLink(), stateType,
                        childTemplate.documentDescription);
            } catch (Exception e2) {
                op.fail(e2);
                return;
            }

            op.complete();
        });
    }

    private static boolean isPostgresDocumentIndexForService(Service service, ServiceHost host) {
        if (service.getDocumentIndexPath() == null
                || service.getDocumentIndexPath().equals(ServiceUriPaths.CORE_DOCUMENT_INDEX)) {
            return host.getDocumentIndexServiceUri().getPath().equals(
                    PostgresDocumentIndexService.SELF_LINK);
        }

        return PostgresDocumentIndexService.SELF_LINK.equals(service.getDocumentIndexPath());
    }

    public static boolean handleSendRequest(ServiceHost host, Operation op,
            Function<String, Service> findService) {
        if (op.getUri() == null) {
            return false;
        }

        String path = op.getUri().getPath();
        if ((ServiceUriPaths.CORE_LOCAL_QUERY_TASKS.equals(path)
                || ServiceUriPaths.CORE_QUERY_TASKS.equals(path))
                && op.getBodyRaw() instanceof QueryTask) {
            QueryTask queryTask = (QueryTask) op.getBodyRaw();
            // Return empty result for synchronization task service query for factory children
            // which are not using PERIODIC_MAINTENANCE option.
            // TODO: Replace with official support in Xenon to disable synchronization for some
            // factories, while still using REPLICATION option.
            if (queryTask.querySpec != null) {
                URI referer = op.getReferer();
                if (DISABLE_SYNCHRONIZE_FACTORIES
                        && referer != null
                        && referer.getPath() != null
                        && referer.getPath().startsWith(SynchronizationTaskService.FACTORY_LINK)
                        && queryTask.querySpec.query != null
                        && queryTask.querySpec.query.booleanClauses != null) {
                    String factoryLink = null;
                    for (QueryTask.Query q : queryTask.querySpec.query.booleanClauses) {
                        if (ServiceDocument.FIELD_NAME_SELF_LINK.equals(q.term.propertyName)
                                && QueryTask.Query.Occurance.MUST_OCCUR.equals(q.occurance)
                                && QueryTask.QueryTerm.MatchType.WILDCARD.equals(q.term.matchType)
                                && q.term.matchValue != null) {
                            factoryLink = UriUtils.getParentPath(q.term.matchValue);
                            break;
                        }
                    }

                    if (factoryLink != null) {
                        Service s = findService.apply(factoryLink);
                        if (s instanceof FactoryService) {
                            // TODO: we might need to enable for services which override
                            // handleNodeGroupMaintenance()
                            logger.info(String.format("Ignored synchronization query: %s", factoryLink));

                            // Disable synchronization for non-periodic services
                            QueryTask result = new QueryTask();
                            result.results = new ServiceDocumentQueryResult();
                            result.results.documents = new HashMap<>();
                            result.results.documentCount = 0L;
                            result.results.queryTimeMicros = 0L;
                            op.setBodyNoCloning(result).complete();
                            return true;
                        }
                    }
                }
            }
        } else if (DISABLE_SYNCHRONIZE_PEERS_REQUEST
                && op.getBodyRaw() instanceof SynchronizePeersRequest) {
            SynchronizePeersRequest request = (SynchronizePeersRequest) op.getBodyRaw();
            if (request.options != null && request.options.contains(ServiceOption.PERSISTENCE)) {
                // Disable synchronization with peers since they use a shared persistence
                // TODO: Review if this have side affects, especially on node restarts when ownership
                // document changes in parallel to doing service updates (PATCH/PUT)
                if (request.state.documentVersion == -1) {
                    op.fail(Operation.STATUS_CODE_NOT_FOUND);
                } else {
                    op.setBodyNoCloning(null);
                    op.complete();
                }
                return true;
            }
        } else if (AuthorizationTokenCacheService.SELF_LINK.equals(path)
                && op.getBodyRaw() instanceof AuthorizationTokenCacheServiceState
                && !op.hasPragmaDirective(PRAGMA_DIRECTIVE_FROM_AUTH_BROADCAST)) {
            // Broadcast auth token cache request to other nodes
            op.nestCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }

                Operation ro = op.clone()
                        .addPragmaDirective(PRAGMA_DIRECTIVE_FROM_AUTH_BROADCAST)
                        .setCompletion((o2, e2) -> {
                            if (e2 != null) {
                                logger.warning(Utils.toString(e2));
                            }

                            op.complete();
                        });

                host.broadcastRequest(ServiceUriPaths.DEFAULT_NODE_SELECTOR, true, ro);
            });
        }

        return false;
    }

    public static void registerForReplicatedServiceAvailability(ServiceHost host,
            CompletionHandler completion, AuthorizationContext authorizationContext,
            String nodeSelectorPath, String... servicePaths) {
        for (String link : servicePaths) {
            Operation op = Operation.createPost(host, link)
                    .setCompletion(completion)
                    .setExpiration(Utils.fromNowMicrosUtc(host.getOperationTimeoutMicros()));

            registerForReplicatedServiceAvailability(host, op, link, nodeSelectorPath,
                    authorizationContext);
        }
    }

    public static void registerForReplicatedServiceAvailability(ServiceHost host, Operation op,
            String servicePath, String nodeSelectorPath, AuthorizationContext authorizationContext) {
        CompletionHandler ch = (o, e) -> {
            if (e != null) {
                if (op.getExpirationMicrosUtc() < Utils.getSystemNowMicrosUtc()) {
                    String msg = "Failed to check replicated service availability";
                    op.fail(new TimeoutException(msg));
                    return;
                }

                // service is not yet available, reschedule
                host.scheduleCore(() -> {
                    registerForReplicatedServiceAvailability(host, op, servicePath, nodeSelectorPath,
                            authorizationContext);
                }, host.getMaintenanceIntervalMicros(), TimeUnit.MICROSECONDS);
                return;
            }
            op.complete();
        };

        URI serviceUri = UriUtils.buildUri(host, servicePath);
        checkReplicatedServiceAvailability(ch, host, serviceUri, nodeSelectorPath,
                authorizationContext);
    }

    private static void checkReplicatedServiceAvailability(CompletionHandler ch,
            ServiceHost host, URI serviceUri, String selectorPath,
            AuthorizationContext authorizationContext) {
        if (selectorPath == null) {
            throw new IllegalArgumentException("selectorPath is required");
        }

        long timeoutMicros = Math.min(host.getOperationTimeoutMicros(),
                TimeUnit.SECONDS.toMicros(NODE_GROUP_UTILS_OPERATION_TIMEOUT_SECONDS));

        // Create operation to retrieve stats. This completion will execute after
        // we determine the owner node
        // TODO: replace with better option to check peer nodes
        OperationContext opContext = OperationContext.getOperationContext();
        Operation get = Operation.createOptions(serviceUri).setCompletion((o, e) -> {
            OperationContext.restoreOperationContext(opContext);
            if (e != null) {
                if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                    ch.handle(o, new IllegalStateException("not available"));
                } else {
                    host.log(Level.WARNING, "%s to %s failed: %s",
                            o.getAction(), o.getUri(), e);
                    ch.handle(null, e);
                }
                return;
            }

            ch.handle(o, null);
        });
        get.setReferer(host.getPublicUri())
                .setExpiration(Utils.fromNowMicrosUtc(timeoutMicros));
        get.setAuthorizationContext(authorizationContext);
        URI nodeSelector = UriUtils.buildUri(host, selectorPath);
        NodeSelectorService.SelectAndForwardRequest req = new NodeSelectorService.SelectAndForwardRequest();
        req.key = serviceUri.getPath();

        Operation selectPost = Operation.createPost(nodeSelector)
                .setReferer(host.getPublicUri())
                .setExpiration(get.getExpirationMicrosUtc())
                .setBodyNoCloning(req);
        selectPost.setCompletion((o, e) -> {
            if (e != null) {
                host.log(Level.WARNING, "SelectOwner for %s to %s failed: %s",
                        req.key, nodeSelector, e.toString());
                ch.handle(get, e);
                return;
            }
            NodeSelectorService.SelectOwnerResponse selectRsp = o.getBody(NodeSelectorService.SelectOwnerResponse.class);
            URI serviceOnOwner = UriUtils.buildUri(selectRsp.ownerNodeGroupReference,
                    serviceUri.getPath());
            get.setUri(serviceOnOwner).sendWith(host);
        }).sendWith(host);
    }

    public interface StartCoreServicesSynchronouslyHandler {
        void accept(Service... services) throws Throwable;
    }

    private static void updateQueryTask(Operation post) {
        if (!post.hasBody()) {
            return;
        }
        try {
            QueryTask queryTask = post.getBody(QueryTask.class);
            if (queryTask == null || queryTask.querySpec == null
                    || queryTask.querySpec.options == null) {
                return;
            }

            // Disable BROADCAST
            if (queryTask.querySpec.options
                    .remove(QueryTask.QuerySpecification.QueryOption.BROADCAST)) {
                // Disable OWNER_SELECTION if doing a BROADCAST query
                queryTask.querySpec.options
                        .remove(QueryTask.QuerySpecification.QueryOption.OWNER_SELECTION);
            }

            // Disable READ_AFTER_WRITE_CONSISTENCY
            queryTask.querySpec.options
                    .remove(QueryTask.QuerySpecification.QueryOption.READ_AFTER_WRITE_CONSISTENCY);

            post.setBodyNoCloning(queryTask);
        } catch (Exception e) {
            // Ignore failure
            logger.warning("Failed to disable BROADCAST query requests: " + Utils.toString(e));
        }
    }

    public static void handleStartDocumentIndexService(ServiceHost host,
            Service documentIndexService,
            NodeSelectorService defaultNodeSelectorService,
            StartCoreServicesSynchronouslyHandler startCoreServicesSynchronously) throws Throwable {
        Service[] queryServiceArray = new Service[] {
                documentIndexService,
                new QueryTaskFactoryService() {
                    @Override
                    public void handlePost(Operation post) {
                        updateQueryTask(post);
                        super.handlePost(post);
                    }
                },
                new LocalQueryTaskFactoryService() {
                    @Override
                    public void handlePost(Operation post) {
                        updateQueryTask(post);
                        super.handlePost(post);
                    }
                },
                TaskFactoryService.create(GraphQueryTaskService.class),
                TaskFactoryService.create(SynchronizationTaskService.class),
                new QueryPageForwardingService(defaultNodeSelectorService) };
        startCoreServicesSynchronously.accept(queryServiceArray);
    }

    public static boolean handleReplicateRequest(ServiceHost host,
            EnumSet<ServiceOption> serviceOptions, ServiceDocument state,
            String selectorPath, String selectionKey, Operation op) {
        // Disable replication for persistent services
        // TODO: Replace with official support in Xenon to disable replication for persistent services
        if (serviceOptions.contains(Service.ServiceOption.PERSISTENCE)) {
            op.complete();
            return true;
        }
        return false;
    }

    static String getResourceAsString(ClassLoader classLoader, String resource) throws IOException {
        try (InputStream is = classLoader.getResourceAsStream(resource)) {
            if (is == null) {
                throw new FileNotFoundException(resource);
            }
            return new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .collect(Collectors.joining("\n"));
        }
    }

    public static void clearCacheOnNodeGroupChange(ServiceHost host) {
        host.registerForServiceAvailability((op, ex) -> {
            if (ex != null) {
                host.log(Level.SEVERE, "Failed register for default node group availability: %s",
                        Utils.toString(ex));
                return;
            }

            // subscribe to node group and reset persisted serviced state cache on every change
            Operation subscribeOp = Operation.createPost(host, ServiceUriPaths.DEFAULT_NODE_GROUP)
                    .setReferer(host.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            host.log(Level.SEVERE, "Failed subscription to default node group: %s",
                                    Utils.toString(e));
                        }
                    });

            host.startSubscriptionService(subscribeOp, notifyOp -> {
                // clear cache to prevent serving stale data for owner selected services due to owner change
                host.log(Level.INFO, "Clear persisted service state cache on node group change");
                host.clearPersistedServiceStateCache();
                notifyOp.complete();
            });
        }, ServiceUriPaths.DEFAULT_NODE_GROUP);
    }

    public static void failServerNotAvailable(Operation inboundOp) {
        Throwable e = new IllegalStateException("Server unavailable");

        inboundOp.setStatusCode(Operation.STATUS_CODE_UNAVAILABLE);
        ServiceErrorResponse rsp = new ServiceErrorResponse();
        rsp.message = e.getMessage();
        rsp.details = EnumSet.of(ServiceErrorResponse.ErrorDetail.SHOULD_RETRY);
        rsp.statusCode = inboundOp.getStatusCode();
        inboundOp.fail(e, rsp);
    }
}
