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

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SSL trust certificate import request.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SslTrustImportRequest {

    /** (Required) The URI of the host where a SSL Trust certificate will be imported. */
    public URI hostUri;

    /**
     * (Optional) Boolean flag indicating whether the certificate should be accepted or will need
     * confirmation from the user. Typically, when self-signed certificates, the service will reject
     * the certificate in order for the user to confirm. Default value is false.
     */
    public boolean acceptCertificate;

    /**
     * (Optional) Resource (compute, registry ...) associate with the trust certificate. It is
     * optional since some CA certificates will not be associated with a single server.
     */
    public String resourceLink;

}
