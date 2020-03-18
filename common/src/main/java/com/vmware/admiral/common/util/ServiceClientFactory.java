/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;

/**
 * Factory for ServiceClient instances with support for providing custom SSLContext
 *
 * TODO cache SSLContext/client for a given sslTrust instead of recreating for every request
 */
public class ServiceClientFactory {

    /**
     * Create a ServiceClient instance using the given TrustManager and KeyManager
     *
     * @param trustManager
     * @param keyManager
     * @return
     */
    public static ServiceClient createServiceClient(TrustManager trustManager,
            KeyManager keyManager) {

        return createServiceClient(CertificateUtil.createSSLContext(
                trustManager, keyManager));
    }

    /**
     * Create a ServiceClient instance using the given TrustManager, KeyManager and requestPayloadSizeLimit
     *
     * @param trustManager
     * @param keyManager
     * @param requestPayloadSizeLimit
     * @return
     */
    public static ServiceClient createServiceClient(TrustManager trustManager,
            KeyManager keyManager, int requestPayloadSizeLimit) {

        return createServiceClient(CertificateUtil.createSSLContext(
                trustManager, keyManager), requestPayloadSizeLimit);
    }

    /**
     * Create a ServiceClient instance using the given SSLContext
     *
     * @param sslContext
     * @return
     */
    public static ServiceClient createServiceClient(SSLContext sslContext) {
        return createServiceClient(sslContext, 0);
    }

    /**
     * Create a ServiceClient instance using the given SSLContext and requestPayloadSizeLimit
     *
     * @param sslContext
     * @param requestPayloadSizeLimit
     * @return
     */
    public static ServiceClient createServiceClient(SSLContext sslContext,
            int requestPayloadSizeLimit) {
        ServiceClient serviceClient;
        try {
            // supply a scheduled executor for re-use by the client, but do not supply our
            // regular executor, since the I/O threads might take up all threads
            serviceClient = NettyHttpServiceClient.create(
                    ServiceClientFactory.class.getCanonicalName(),
                    null,
                    Executors.newScheduledThreadPool(Utils.DEFAULT_THREAD_COUNT, r -> new Thread(
                            r, ServiceClientFactory.class.getSimpleName())));

            if (requestPayloadSizeLimit > 0) {
                serviceClient.setRequestPayloadSizeLimit(requestPayloadSizeLimit);
            }
            serviceClient.setSSLContext(sslContext);
            serviceClient.start();

            return serviceClient;

        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create ServiceClient", e);
        }
    }
}
