/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.xenon.common.ServiceHost;

public class SslCertificateResolverIT {

    @Before
    public void before() {
        ServerX509TrustManager.init(new ServiceHost() { });
    }

    @Test
    public void testResolveCertificates() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        SslCertificateResolver.execute(URI.create("https://mail.google.com"),
                (resolver, ex) -> {
                    try {
                        assertNull(ex);
                        assertTrue(resolver.isCertsTrusted());
                        assertNotNull(resolver.getCertificate());
                    } finally {
                        latch1.countDown();
                    }
                });
        assertTrue(latch1.await(60, TimeUnit.SECONDS));

        final CountDownLatch latch2 = new CountDownLatch(1);
        SslCertificateResolver.execute(URI.create("https://email.vmware.com"),
                (resolver, ex) -> {
                    try {
                        assertNull(ex);
                        assertTrue(resolver.isCertsTrusted());
                        assertNotNull(resolver.getCertificate());
                    } finally {
                        latch2.countDown();
                    }
                });
        assertTrue(latch2.await(60, TimeUnit.SECONDS));

        // self-signed cert should not be trusted
        final CountDownLatch latch3 = new CountDownLatch(1);

        SslCertificateResolver.execute(URI.create("https://vcac-be.eng.vmware.com"),
                (resolver, ex) -> {
                    try {
                        assertNull(ex);
                        assertFalse(resolver.isCertsTrusted());
                        assertNotNull(resolver.getCertificate());
                    } finally {
                        latch3.countDown();
                    }
                });
        assertTrue(latch3.await(60, TimeUnit.SECONDS));
    }

}
