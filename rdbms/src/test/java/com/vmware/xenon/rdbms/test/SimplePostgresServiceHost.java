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

package com.vmware.xenon.rdbms.test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager;
import com.vmware.xenon.services.rdbms.PostgresServiceHost;

public class SimplePostgresServiceHost extends PostgresServiceHost {

    public static final String LATEST_SNAPSHOT_RESOURCE_PATH =
            "liquibase/test-host/latest-snapshot.json";
    public static final String CHANGELOG_RESOURCE_PATH = "liquibase/test-host/changelog.xml";

    public SimplePostgresServiceHost() {
        this.latestSnapshotResourcePath = LATEST_SNAPSHOT_RESOURCE_PATH;
        this.changelogResourcePath = CHANGELOG_RESOURCE_PATH;
    }

    @Override
    public ServiceHost start() throws Throwable {
        setRejectRemoteRequests(true);
        super.start();
        startDefaultCoreServicesSynchronously(false);
        startFactory(new TestService());
        startFactory(new TestPeriodicService());
        startFactory(new TestImmutableService());

        scheduleCore(() -> {
            setRejectRemoteRequests(false);
            joinPeers(getInitialPeerHosts(), ServiceUriPaths.DEFAULT_NODE_GROUP);
        }, 1, TimeUnit.SECONDS);

        return this;
    }

    @Override
    protected void registerPostgresSchema(PostgresSchemaManager sm) {
        super.registerPostgresSchema(sm);
        sm.addFactory(TestService.class);
        sm.addFactory(TestPeriodicService.class);
        sm.addFactory(TestImmutableService.class);
    }

    public static void main(String[] args) throws Throwable {
        SimplePostgresServiceHost h = new SimplePostgresServiceHost();
        h.initialize(args);
        h.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));
    }
}
