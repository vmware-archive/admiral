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

import com.jcraft.jsch.UserInfo;

/**
 * Create a UserInfo object for a specific password
 */
public class PasswordUserInfo implements UserInfo {
    private final String password;

    public PasswordUserInfo(String password) {
        this.password = password;
    }

    @Override
    public String getPassphrase() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean promptPassword(String message) {
        return true;
    }

    @Override
    public boolean promptPassphrase(String message) {
        return false;
    }

    @Override
    public boolean promptYesNo(String message) {
        return false;
    }

    @Override
    public void showMessage(String message) {
        throw new UnsupportedOperationException();
    }

}
