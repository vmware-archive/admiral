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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.common.SshUntrustedServerException.COMMENT_PROP_NAME;
import static com.vmware.admiral.common.SshUntrustedServerException.FINGERPRINT_PROP_NAME;
import static com.vmware.admiral.common.SshUntrustedServerException.HOST_KEY_PROP_NAME;
import static com.vmware.admiral.common.SshUntrustedServerException.HOST_PROP_NAME;
import static com.vmware.admiral.common.SshUntrustedServerException.KEY_TYPE_PROP_NAME;

import java.util.HashMap;
import java.util.Map;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import com.vmware.admiral.common.SshUntrustedServerException;

/**
 * HostKeyRepository decorating an existing repository with additional behavior for exposing
 * unknown/invalid host keys
 *
 * When connecting to an unknown/invalid host an UntrustedServerException will be thrown, containing
 * the server's host key
 */
public class HostKeyRepositoryWrapper implements HostKeyRepository {
    private final JSch jsch;
    private final HostKeyRepository wrapped;

    public HostKeyRepositoryWrapper(JSch jsch, HostKeyRepository wrapped) {
        this.jsch = jsch;
        this.wrapped = wrapped;
    }

    @Override
    public int check(String host, byte[] key) {
        int result = wrapped.check(host, key);
        if (result != OK) {
            try {
                HostKey hostKey = new HostKey(host, key);

                Map<String, String> identification = new HashMap<>();
                identification.put(HOST_KEY_PROP_NAME, hostKey.getKey());
                identification.put(HOST_PROP_NAME, hostKey.getHost());
                identification.put(KEY_TYPE_PROP_NAME, hostKey.getType());
                identification.put(FINGERPRINT_PROP_NAME, hostKey.getFingerPrint(jsch));
                identification.put(COMMENT_PROP_NAME, hostKey.getComment());

                // TODO different sublcasses for NOT_INCLUDED and CHANGED
                throw new SshUntrustedServerException(identification);

            } catch (JSchException x) {
                throw new RuntimeException("Error creating host key: " + x.getMessage(), x);
            }
        }

        return result;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        wrapped.add(hostkey, ui);
    }

    @Override
    public void remove(String host, String type) {
        wrapped.remove(host, type);
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        wrapped.remove(host, type, key);
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return wrapped.getKnownHostsRepositoryID();
    }

    @Override
    public HostKey[] getHostKey() {
        return wrapped.getHostKey();
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        return wrapped.getHostKey(host, type);
    }

}
