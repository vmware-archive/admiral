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

package com.vmware.admiral.adapter.pks.entities;

import com.google.gson.annotations.SerializedName;

public class KubeConfig {

    public static class Token {

        @SerializedName("token")
        public String token;
    }

    public static class AuthInfo {

        @SerializedName("kind")
        public String name;

        @SerializedName("user")
        public Token user;
    }

    @SerializedName("kind")
    public String kind;

    @SerializedName("apiVersion")
    public String apiVersion;

    @SerializedName("preferences")
    public Object preferences;

    @SerializedName("clusters")
    public Object[] clusters;

    @SerializedName("users")
    public AuthInfo[] users;

    @SerializedName("contexts")
    public Object[] contexts;

    @SerializedName("current-context")
    public String currentContext;

    @SerializedName("extensions")
    public Object[] extensions;
}
