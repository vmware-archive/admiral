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

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

/**
 * Create an identity from an SSH private key
 */
public class PublicKeyIdentity implements Identity {
    private final KeyPair keyPair;

    public PublicKeyIdentity(JSch jsch, byte[] privateKey, byte[] publicKey)
            throws JSchException {
        this.keyPair = KeyPair.load(jsch, privateKey, publicKey);
    }

    @Override
    public boolean setPassphrase(byte[] passphrase) throws JSchException {
        return false;
    }

    @Override
    public boolean isEncrypted() {
        return false;
    }

    @Override
    public byte[] getSignature(byte[] data) {
        return keyPair.getSignature(data);
    }

    @Override
    public byte[] getPublicKeyBlob() {
        return keyPair.getPublicKeyBlob();
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAlgName() {
        return "ssh-rsa"; // FIXME
    }

    @Deprecated
    @Override
    public boolean decrypt() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}