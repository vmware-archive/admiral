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

package com.vmware.photon.controller.model.support;

import java.util.Map;

import com.vmware.xenon.common.Utils;

/**
 * Certificate information holder
 */
public class CertificateInfo {
    public static final String KIND = Utils.buildKind(CertificateInfo.class);
    public String documentKind = KIND;
    /**
     * The certificate in string format. e.g. PEM for X509Certificate, ssh host key, etc
     */
    public final String certificate;

    /**
     * Certificate related properties which may provide additional information about the given
     * certificate.
     */
    public final Map<String, String> properties;

    private CertificateInfo(String certificate, Map<String, String> properties) {
        if (certificate == null) {
            throw new IllegalArgumentException("'certificate' must be set.");
        }
        this.certificate = certificate;
        this.properties = properties;
    }

    public static CertificateInfo of(String certificate, Map<String, String> properties) {
        return new CertificateInfo(certificate, properties);
    }

    @Override
    public String toString() {
        return String.format("%s[certificate=%s, properties=%s]",
                getClass().getSimpleName(), this.certificate, this.properties);
    }
}
