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

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.Utils;

/**
 * Certificate related service error response used to set as operation body when there is some issue
 * with certificates.
 */

public class CertificateInfoServiceErrorResponse extends ServiceErrorResponse {
    /**
     * The common mask for certificate info related error responses
     */
    public static final int ERROR_CODE_CERTIFICATE_MASK = 0x90000000;
    /**
     * indicates the certificate is not trusted
     */
    public static final int ERROR_CODE_UNTRUSTED_CERTIFICATE = ERROR_CODE_CERTIFICATE_MASK
            | 0x00000001;

    public static final String KIND = Utils.buildKind(CertificateInfoServiceErrorResponse.class);

    /**
     * The certificate information.
     */
    public CertificateInfo certificateInfo;

    private CertificateInfoServiceErrorResponse(CertificateInfo certificateInfo,
            int statusCode, int errorCode, String message) {
        if (certificateInfo == null) {
            throw new IllegalArgumentException("'certificateInfo' must be set.");
        }
        this.documentKind = KIND;
        this.certificateInfo = certificateInfo;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static CertificateInfoServiceErrorResponse create(
            CertificateInfo certificateInfo,
            int statusCode, int errorCode, String message) {
        return new CertificateInfoServiceErrorResponse(
                certificateInfo, statusCode, errorCode, message);
    }

    public static CertificateInfoServiceErrorResponse create(
            CertificateInfo certificateInfo,
            int statusCode, int errorCode, Throwable e) {
        return create(certificateInfo, statusCode, errorCode, e != null ? e.getMessage() : null);
    }
}
