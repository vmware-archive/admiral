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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.rdbms.PostgresHostUtils;

public class TestUtils {
    private static final Logger logger = Logger.getLogger(TestUtils.class.getName());
    private static ExecutorService executorService = null;
    private static DataSource ds = null;
    private static final Map<String, AtomicInteger> testDatabaseNames = new HashMap<>();

    private static final String TEST_DATABASE_PREFIX = XenonConfiguration.string(
            TestUtils.class,
            "testDatabasePrefix",
            "test_");

    private static final boolean DROP_TEST_DATABASE_ON_SHUTDOWN = XenonConfiguration.bool(
            TestUtils.class,
            "dropTestDatabaseOnShutdown",
            true);

    static {
        if (DROP_TEST_DATABASE_ON_SHUTDOWN) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                testDatabaseNames.keySet().forEach(name -> {
                    try {
                        TestUtils.dropDatabase(name);
                    } catch (SQLException e) {
                        logger.warning(String.format("Failed to drop database %s: %s", name, e));
                    }
                });
            }));
        }
    }

    public interface RunnableWithThrowable {
        void run(int i) throws Throwable;

    }

    private TestUtils() {
    }

    public static String getTestDatabaseName() {
        String databaseName = null;
        boolean createDatabase = false;

        synchronized (testDatabaseNames) {
            for (Map.Entry<String, AtomicInteger> entry : testDatabaseNames.entrySet()) {
                if (entry.getValue().get() <= 0) {
                    databaseName = entry.getKey();
                    entry.getValue().set(1);
                    break;
                }
            }
            if (databaseName == null) {
                databaseName = TEST_DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
                testDatabaseNames.put(databaseName, new AtomicInteger(1));
                createDatabase = true;
            }
        }

        if (createDatabase) {
            try {
                createDatabase(databaseName);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        Properties props = PostgresHostUtils.getDataSourceProperties();
        props.setProperty("dataSource.databaseName", databaseName);
        props.setProperty("minimumIdle", "0");

        DataSource ds = PostgresHostUtils.createDataSource(props);
        try {
            if (createDatabase) {
                dropAll(ds);
            } else {
                deleteAll(ds);
            }
        } catch (SQLException e) {
            logger.severe("Failed to drop/delete database: " + e);
            throw new IllegalStateException(e);
        } finally {
            PostgresHostUtils.closeDataSource(ds);
        }

        logger.info("getTestDatabaseName: " + databaseName);
        return databaseName;
    }

    public static void decreaseTestDatabaseRefCount(String databaseName) {
        if (databaseName == null) {
            return;
        }
        synchronized (testDatabaseNames) {
            int count = testDatabaseNames.computeIfAbsent(databaseName, key -> new AtomicInteger())
                    .decrementAndGet();
            logger.info(String.format("decreaseTestDatabaseRefCount: %s (count: %d)", databaseName,
                    count));
        }
    }

    public static void increaseTestDatabaseRefCount(String databaseName) {
        if (databaseName == null) {
            return;
        }

        synchronized (testDatabaseNames) {
            int count = testDatabaseNames.computeIfAbsent(databaseName, key -> new AtomicInteger())
                    .incrementAndGet();
            logger.info(String.format("increaseTestDatabaseRefCount: %s (count: %d)", databaseName,
                    count));
        }
    }

    public static List<String> getDatabaseNames(String prefix) {
        return getDatabaseNames().stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    public static List<String> getDatabaseNames() {
        List<String> databases = new ArrayList<>();
        String sql = "SELECT datname FROM pg_database WHERE datistemplate = FALSE";
        try (Connection conn = getDataSource().getConnection();
                Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String databaseName = rs.getString(1);
                    databases.add(databaseName);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get database names: " + e);
            throw new IllegalStateException(e);
        }
        return databases;
    }

    public static List<String> getTestDatabaseNames() {
        return getDatabaseNames(TEST_DATABASE_PREFIX);
    }

    public static void dropAllTestDatabases() throws SQLException {
        List<String> databases = getTestDatabaseNames();
        for (String database : databases) {
            dropDatabase(database);
        }
    }

    public static void createDatabase(String databaseName) throws SQLException {
        logger.info("Create database: " + databaseName);
        try {
            try (Connection conn = getDataSource().getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE " + databaseName);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("exists")) {
                return;
            }
            logger.severe(String.format("Failed to create database %s: %s", databaseName, e));

            throw e;
        }
    }

    public static void dropDatabase(String databaseName) throws SQLException {
        logger.info("Drop database: " + databaseName);
        try {
            try (Connection conn = getDataSource().getConnection();
                    Statement stmt = conn.createStatement()) {
                // Terminate connections to database
                String sql = String.format("SELECT pg_terminate_backend(pg_stat_activity.pid) "
                        + "FROM pg_stat_activity "
                        + "WHERE pg_stat_activity.datname = '%s' "
                        + "AND pid <> pg_backend_pid()", databaseName);
                stmt.executeQuery(sql);

                // Drop database
                stmt.executeUpdate("DROP DATABASE " + databaseName);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("does not exist")) {
                return;
            }
            logger.warning(String.format("Failed to drop database %s: %s", databaseName, e));
            throw e;
        }
    }

    public static synchronized DataSource getDataSource() {
        if (ds == null) {
            Properties props = PostgresHostUtils.getDataSourceProperties();
            props.setProperty("minimumIdle", "0");
            ds = PostgresHostUtils.createDataSource(props);
        }
        return ds;
    }

    public static synchronized void closeDataSource() {
        if (ds != null) {
            PostgresHostUtils.closeDataSource(ds);
            ds = null;
        }
    }

    public static void deleteAll(DataSource ds) throws SQLException {
        PostgresHostUtils.deleteAll(ds);
    }

    public static void dropAll(DataSource ds) throws SQLException {
        PostgresHostUtils.dropAll(ds);
    }

    public static void runParallel(VerificationHost host, int count,
            RunnableWithThrowable runnable) {
        runParallel(host.getTimeoutSeconds(), count, runnable);
    }

    public static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(10);
        }
        return executorService;
    }

    public static void runParallel(long timeoutSeconds, int count, RunnableWithThrowable runnable) {
        runParallel(getExecutorService(), timeoutSeconds, count, runnable);
    }

    public static void runParallel(ExecutorService es, long timeoutSeconds, int count,
            RunnableWithThrowable runnable) {
        TestContext tc = TestContext.create(count, TimeUnit.SECONDS.toMicros(timeoutSeconds));
        for (int i = 0; i < count; i++) {
            int i2 = i;
            es.submit(() -> {
                try {
                    runnable.run(i2);
                    tc.complete();
                } catch (Throwable e) {
                    tc.fail(e);
                }
            });
        }
        tc.await();
    }

    public static Service findFactoryService(StatefulService service) {
        return findService(service.getHost(), UriUtils.getParentPath(service.getSelfLink()));
    }

    public static void findFactoryService(StatefulService service,
            Consumer<FactoryService> handler) {
        findService(service.getHost(), UriUtils.getParentPath(service.getSelfLink()), s -> {
            handler.accept((FactoryService) s);
        });
    }

    public static void findService(ServiceHost host, String path, Consumer<Service> handler) {
        Service s = findService(host, path);
        if (s != null) {
            handler.accept(s);
        }
    }

    public static Service findService(ServiceHost host, String path) {
        try {
            Class<?> clazz = host.getClass();
            while (ServiceHost.class.isAssignableFrom(clazz.getSuperclass())) {
                clazz = clazz.getSuperclass();
            }
            Method findService = clazz.getDeclaredMethod("findService", String.class);
            findService.setAccessible(true);
            return (Service) findService.invoke(host, path);
        } catch (Exception e) {
            logger.warning("Failed to access findService method: " + e);
        }

        return null;
    }

    public static void setLongTimeout(ServiceHost host, TestRequestSender sender) {
        host.setOperationTimeOutMicros(TimeUnit.HOURS.toMicros(1));
        sender.setTimeout(Duration.ofHours(1));
    }

    public static void setLongTimeout(VerificationHost host) {
        setLongTimeout(host, host.getTestRequestSender());
    }

    public static void initializeServiceHost(ServiceHost host, ServiceHost.Arguments args,
            String databaseName)
            throws Throwable {
        System.setProperty("hikari.dataSource.databaseName", databaseName);
        try {
            host.initialize(args);
        } finally {
            System.getProperties().remove("hikari.dataSource.databaseName");
        }
    }

}
