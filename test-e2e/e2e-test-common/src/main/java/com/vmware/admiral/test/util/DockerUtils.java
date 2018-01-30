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

package com.vmware.admiral.test.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificatesStore;
import com.spotify.docker.client.DockerClient;

public class DockerUtils {

    public static DockerClient createUnsecureDockerClient(String uri) {

        DockerCertificatesStore store = new DockerCertificatesStore() {

            @Override
            public SSLContext sslContext() {
                return HttpUtils.newUnsecureSSLContext();
            }

            @Override
            public HostnameVerifier hostnameVerifier() {
                return HttpUtils.allowAllHostNameVeririer();
            }
        };
        return DefaultDockerClient.builder()
                .uri(uri)
                .dockerCertificates(store)
                .readTimeoutMillis(120000)
                .connectTimeoutMillis(120000)
                .build();
    }

}
