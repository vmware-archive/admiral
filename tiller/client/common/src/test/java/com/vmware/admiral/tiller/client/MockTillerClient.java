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

package com.vmware.admiral.tiller.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vmware.admiral.tiller.client.TillerClient;
import com.vmware.admiral.tiller.client.TillerClientException;

public class MockTillerClient implements TillerClient {

    private AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void close() throws Exception {
        this.closed.set(true);
    }

    @Override
    public CompletableFuture<Void> healthCheck() {
        assertNotClosed();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isClosed() {
        return this.closed.get();
    }

    private void assertNotClosed() {
        if (this.closed.get()) {
            throw new TillerClientException("Client is closed");
        }
    }

}
