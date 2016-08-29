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

import java.util.Arrays;

/**
 * Parameters needed for creating an SSH session
 *
 * This include the target host and authentication but not the actual command that needs to be
 * executed (those are channel params, not session params).
 */
public class SessionParams {
    private String host;
    private int port;
    private String user;
    private String hostKey;
    private String password;
    private byte[] privateKey;

    public String getHost() {
        return host;
    }

    public SessionParams withHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public SessionParams withPort(int port) {
        this.port = port;
        return this;
    }

    public String getUser() {
        return user;
    }

    public SessionParams withUser(String user) {
        this.user = user;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public SessionParams withPassword(String password) {
        this.password = password;
        return this;
    }

    public byte[] getPrivateKey() {
        return privateKey == null ? null : privateKey.clone();
    }

    public SessionParams withPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey == null ? null : privateKey.clone();
        return this;
    }

    public String getHostKey() {
        return hostKey;
    }

    public SessionParams withHostKey(String hostKey) {
        this.hostKey = hostKey;
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        result = prime * result + ((hostKey == null) ? 0 : hostKey.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + port;
        result = prime * result + Arrays.hashCode(privateKey);
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SessionParams other = (SessionParams) obj;
        if (host == null) {
            if (other.host != null) {
                return false;
            }
        } else if (!host.equals(other.host)) {
            return false;
        }
        if (hostKey == null) {
            if (other.hostKey != null) {
                return false;
            }
        } else if (!hostKey.equals(other.hostKey)) {
            return false;
        }
        if (password == null) {
            if (other.password != null) {
                return false;
            }
        } else if (!password.equals(other.password)) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (!Arrays.equals(privateKey, other.privateKey)) {
            return false;
        }
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "SessionParams [hashCode=" + hashCode() + "]";
    }

}
