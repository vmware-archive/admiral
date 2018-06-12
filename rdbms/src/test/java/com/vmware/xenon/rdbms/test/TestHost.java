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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class TestHost extends PostgresVerificationHost {
    private static int operationLeakDetectorPendingSeconds = 0;

    private Set<PendingOperation> pendingOps = ConcurrentHashMap.newKeySet();
    private Thread pendingThread;

    private static class PendingOperation {
        Operation op;
        long time;
        Throwable throwable;
    }

    public static VerificationHost create() {
        return new TestHost();
    }

    public static VerificationHost create(Integer port) throws Exception {
        ServiceHost.Arguments args = buildDefaultServiceHostArguments(port);
        return initialize(new TestHost(), args);
    }

    public static VerificationHost create(ServiceHost.Arguments args)
            throws Exception {
        return initialize(new TestHost(), args);
    }

    @Override
    public ServiceHost start() throws Throwable {
        startOperationLeakDetector();

        setRejectRemoteRequests(true);
        super.start();
        startFactory(new TestService());
        startFactory(new TestPeriodicService());
        startFactory(new TestImmutableService());
        setRejectRemoteRequests(false);
        return this;
    }

    private void startOperationLeakDetector() {
        if (operationLeakDetectorPendingSeconds <= 0) {
            return;
        }

        this.pendingOps.clear();

        if (this.pendingThread != null) {
            return;
        }

        this.pendingThread = new Thread(() -> {
            try {
                while (!isStopping()) {
                    long now = System.currentTimeMillis();
                    long deadline =
                            now - TimeUnit.SECONDS.toMillis(operationLeakDetectorPendingSeconds);
                    Iterator<PendingOperation> iterator = this.pendingOps.iterator();
                    while (iterator.hasNext()) {
                        PendingOperation po = iterator.next();
                        if (po.op.getCompletion() == null) {
                            if (po.time < deadline) {
                                log(Level.WARNING,
                                        "Result of pending request for %s seconds: %s %s referer=%s statusCode=%s",
                                        TimeUnit.MILLISECONDS.toSeconds(now - po.time),
                                        po.op.getAction(), po.op.getUri(), po.op.getReferer(),
                                        po.op.getStatusCode());
                            }
                            iterator.remove();
                            continue;
                        }
                        if (po.time < deadline) {
                            log(Level.WARNING,
                                    "Pending request for %s seconds: %s %s referer=%s\n%s",
                                    TimeUnit.MILLISECONDS.toSeconds(now - po.time),
                                    po.op.getAction(),
                                    po.op.getUri(), po.op.getReferer(),
                                    Utils.toString(po.throwable));
                        }
                    }
                    try {
                        TimeUnit.SECONDS.sleep(operationLeakDetectorPendingSeconds / 2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                this.pendingOps.clear();
            }
        });
        this.pendingThread.setDaemon(true);
        this.pendingThread.start();
    }


    @Override
    public void sendRequest(Operation op) {
        try {
            super.sendRequest(op);
        } finally {
            if (operationLeakDetectorPendingSeconds > 0 && op.getCompletion() != null) {
                PendingOperation po = new PendingOperation();
                po.op = op;
                po.time = System.currentTimeMillis();
                po.throwable = new Throwable();
                this.pendingOps.add(po);
            }
        }
    }

}
