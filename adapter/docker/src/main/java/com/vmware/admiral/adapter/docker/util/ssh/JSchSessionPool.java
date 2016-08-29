/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.util.ssh;

import com.jcraft.jsch.Session;

/**
 * Provide a pool of JSch Sessions
 */
public interface JSchSessionPool {

    /**
     * Get a connected session
     *
     * @param newTask
     */
    public Session getSession(SessionParams sessionParams);

    /**
     * Signal completion of a task, so the session can be closed or reused
     *
     * @param completedTask
     */
    public void closeSession(Session finishedSession);

    /**
     * Close all active session and reject new requests
     */
    public void shutdown();
}
