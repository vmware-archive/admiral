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

package com.vmware.admiral.adapter.tiller.client;

import java.util.concurrent.CompletableFuture;

/**
 * A client for execution of commands on a remote Tiller instance. Instances are automatically
 * opened. They can be closed by calling {@link #close()}. A closed client should throw
 * {@link TillerClientException} on any command after the client has been closed.
 */
public interface TillerClient extends AutoCloseable {

    /**
     * Checks Tiller overall health.
     *
     * @return a {@link CompletableFuture} that will be completed if the health check succeeds or
     *         will be failed otherwise.
     * @throws TillerClientException
     *             if the client has been destroyed
     */
    public CompletableFuture<Void> healthCheck();

    /**
     * @return whether this client has been closed. All calls to methods will fail after the client
     *         has been closed.
     */
    public boolean isClosed();

}
