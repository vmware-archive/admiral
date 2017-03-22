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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.SslCertificateResolver;

public class SslCertificateResolverTest {
    private SslCertificateResolver resolver;

    @Ignore("Test is working but ignored because of external/vpn network requirements.")
    @Test
    public void testResolveCertificates() throws Exception {
        resolver = SslCertificateResolver.connect(URI.create("https://mail.google.com"));
        assertTrue(resolver.isCertsTrusted());
        assertNotNull(resolver.getCertificate());

        resolver = SslCertificateResolver.connect(URI.create("https://email.vmware.com"));
        assertTrue(resolver.isCertsTrusted());
        assertNotNull(resolver.getCertificate());

        // self-signed cert should not be trusted
        resolver = SslCertificateResolver.connect(URI.create("https://vcac-be.eng.vmware.com"));
        assertFalse(resolver.isCertsTrusted());
        assertNotNull(resolver.getCertificate());

    }
}
