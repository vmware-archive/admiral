/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.util;

public class AuthContext {

    private final String target;
    private final String username;
    private final String password;

    public AuthContext(String target, String username, String password) {
        this.target = target;
        this.username = username;
        this.password = password;
    }

    public String getTarget() {
        return target;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
