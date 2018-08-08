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

package com.vmware.admiral.test.util.host;

import java.util.Objects;

import com.vmware.admiral.test.util.HostType;

public class ContainerHost {

    private String ip;
    private int port;
    private HostType hostType;
    private String serverCertificate;
    private String clientPrivateKey;
    private String clientPublicKey;
    private String sshUsername;
    private String sshKey;
    private AuthKeyType keyType;

    public ContainerHost() {
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setHostType(HostType hostType) {
        this.hostType = hostType;
    }

    public int getPort() {
        return port;
    }

    public HostType getHostType() {
        return hostType;
    }

    public boolean hasServerCertificate() {
        return !Objects.isNull(serverCertificate);
    }

    public String getServerCertificate() {
        return serverCertificate;
    }

    public boolean hasClientVerification() {
        return !Objects.isNull(clientPublicKey);
    }

    public String getClientPrivateKey() {
        return clientPrivateKey;
    }

    public String getClientPublicKey() {
        return clientPublicKey;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public String getSshKey() {
        return sshKey;
    }

    public AuthKeyType getKeyType() {
        return keyType;
    }

    public void setServerCertificate(String serverCertificate) {
        Objects.requireNonNull(serverCertificate, "Parameter 'serverCertificate' cannot be null");
        this.serverCertificate = serverCertificate;
    }

    public void setSshCredentials(String sshUsername, String sshKey, AuthKeyType keyType) {
        Objects.requireNonNull(sshUsername, "Parameter sshUsername 'sshUsername' cannot be null");
        Objects.requireNonNull(sshKey, "Parameter 'sshKey' cannot be null");
        Objects.requireNonNull(keyType, "Parameter 'keyType' cannot be null");
        this.sshUsername = sshUsername;
        this.sshKey = sshKey;
        this.keyType = keyType;
    }

    public void setClientKeyAndCertificate(String clientKey, String clientCertificate) {
        Objects.requireNonNull(clientKey, "Parameter 'clientKey' cannot be null");
        Objects.requireNonNull(clientCertificate, "Parameter 'clientCertificate' cannot be null");
        this.clientPrivateKey = clientKey;
        this.clientPublicKey = clientCertificate;
    }

}
