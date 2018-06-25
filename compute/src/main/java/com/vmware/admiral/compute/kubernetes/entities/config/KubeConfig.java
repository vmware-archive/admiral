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

package com.vmware.admiral.compute.kubernetes.entities.config;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;

/*
 * Returned by 'pks get-credentials' command.
 */
public class KubeConfig extends BaseKubernetesObject {

    public static class Cluster {

        public String server;

        @SerializedName("certificate-authority-data")
        public String certificateAuthorityData;

        @SerializedName("insecure-skip-tls-verify")
        public Boolean insecureSkipTlsVerify;
    }

    public static class ClusterEntry {

        public String name;

        public Cluster cluster;
    }

    public static class Context {

        public String cluster;

        public String user;
    }

    public static class ContextEntry {

        public String name;

        public Context context;
    }

    public static class AuthInfo {

        public String token;

        @SerializedName("client-certificate-data")
        public String clientCertificateData;

        @SerializedName("client-key-data")
        public String clientKeyData;
    }

    public static class UserEntry {

        public String name;

        public AuthInfo user;
    }

    public List<ClusterEntry> clusters;

    public List<ContextEntry> contexts;

    public List<UserEntry> users;

    @SerializedName("current-context")
    public String currentContext;
}
