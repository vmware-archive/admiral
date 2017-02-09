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

package com.vmware.admiral.test.integration.client;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * SSL trust certificate.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:service:common:SslTrustCertificateService:SslTrustCertificateState")
public class SslTrustCertificateState extends TenantedServiceDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    /** (Required) The SSL trust certificate encoded into .PEM format */
    public String certificate;

    /** (Optional) Resource (compute, registry ...) associate with the trust certificate. */
    public String resourceLink;

    /** (Read Only) The common name of the certificate. */
    public String commonName;

    /** (Read Only) The issuer name of the certificate. */
    public String issuerName;

    /** (Read Only) The serial of the certificate. */
    public String serial;

    /** (Read Only) The fingerprint of the certificate in SHA-1 form. */
    public String fingerprint;

    /** (Read Only) The date since the certificate is valid. */
    public long validSince;

    /** (Read Only) The date until the certificate is valid. */
    public long validTo;
}
