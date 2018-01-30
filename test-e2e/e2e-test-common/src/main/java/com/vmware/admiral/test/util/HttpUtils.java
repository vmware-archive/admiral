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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Objects;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

public class HttpUtils {

    public static HttpClient createUnsecureHttpClient(CookieStore cookieStore,
            Collection<? extends Header> defaultHeaders) {
        HttpClientBuilder builder = HttpClientBuilder.create()
                .setSSLContext(HttpUtils.newUnsecureSSLContext())
                .setSSLHostnameVerifier(HttpUtils.allowAllHostNameVeririer())
                .setDefaultRequestConfig(
                        RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
                .disableRedirectHandling();
        if (Objects.nonNull(cookieStore)) {
            builder.setDefaultCookieStore(cookieStore);
        }
        if (Objects.nonNull(defaultHeaders)) {
            builder.setDefaultHeaders(defaultHeaders);
        }
        return builder.build();
    }

    public static SSLContext newUnsecureSSLContext() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }
            } }, new java.security.SecureRandom());
            return sslcontext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Cannot create SSL Context.");
        }
    }

    public static HostnameVerifier allowAllHostNameVeririer() {
        return new HostnameVerifier() {

            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }

        };
    }

}
