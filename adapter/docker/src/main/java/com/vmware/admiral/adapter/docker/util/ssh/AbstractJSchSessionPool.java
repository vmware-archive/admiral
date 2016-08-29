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

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.bouncycastle.util.encoders.Base64;

import com.vmware.admiral.adapter.docker.service.HostKeyRepositoryWrapper;

/**
 * Base class for JSchSessionPool implementations
 */
public abstract class AbstractJSchSessionPool implements JSchSessionPool {
    private static final Logger logger = Logger.getLogger(AbstractJSchSessionPool.class.getName());

    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = Long.getLong(
            "adapter.docker.ssh.timeout.millis", TimeUnit.SECONDS.toMillis(30));

    protected Session createNewSession(SessionParams sessionParams) {
        JSch jsch = new JSch();
        try {
            Session newSession = jsch.getSession(sessionParams.getUser(), sessionParams.getHost(),
                    sessionParams.getPort());

            byte[] privateKey = sessionParams.getPrivateKey();
            if (privateKey != null) {
                jsch.addIdentity(new PublicKeyIdentity(jsch, privateKey, null), null);
            }

            String password = sessionParams.getPassword();
            newSession.setUserInfo(new PasswordUserInfo(password));

            HostKeyRepository hostKeyRepository = newSession.getHostKeyRepository();
            newSession.setHostKeyRepository(new HostKeyRepositoryWrapper(jsch, hostKeyRepository));

            String hostKey = sessionParams.getHostKey();
            if (hostKey != null) {
                byte[] hostKeyBytes = Base64.decode(hostKey);
                hostKeyRepository.add(new HostKey(sessionParams.getHost(), hostKeyBytes), null);
            }

            newSession.connect((int) DEFAULT_CONNECTION_TIMEOUT_MILLIS);

            return newSession;

        } catch (JSchException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Session's disconnect method catches all exceptions, so this is just in case it changes in the
     * future
     *
     * @param session
     */
    protected void safeDisconnect(Session session) {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }

        } catch (Exception x) {
            logger.warning("Failed to disconnect session: " + x.getMessage());
        }
    }

}
