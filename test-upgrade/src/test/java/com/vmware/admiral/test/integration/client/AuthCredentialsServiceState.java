/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
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
import java.net.URI;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * ContainerAuthCredentials contains the details about the credentials that are required to connect
 * to the container's host machine.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:xenon:services:common:AuthCredentialsService:AuthCredentialsServiceState")
public class AuthCredentialsServiceState extends TenantedServiceDocument implements Serializable {
    private static final long serialVersionUID = -1993278324364608685L;

    /**
     * Username to connect to. Only needed in case of Password type.
     */
    public String userLink;
    /**
     * User email address (Optional)
     */
    public String userEmail;

    /**
     * If Certificate based Auth, then it contains the certificate of the client (vRA). Else it
     * contains the SSH password. If the type is of PASSWORD, then this field will contain password.
     * If the type is of PUBLIC_KEY, then this field will contain private key of the format .PEM.
     */
    public String privateKey;

    /**
     * If Certificate based Auth, then it contains the Private key of the client (vRA). If the type
     * is of PUBLIC_KEY, then this will contain the client certificate information of the format
     * .PEM
     */
    public String privateKeyId;

    /**
     * Service Account public key
     *
     * When using the BasicAuthenticationService, this is not used. Other authentication services
     * may use it.
     */
    public String publicKey;

    /** Token server URI. */
    public URI tokenReference;

    /**
     * String representation of AuthCredentialType enum. Possible values will be PASSWORD /
     * PUBLIC_KEY which says whether username / password way of authentication or certificate based
     */
    public String type;

    /**
     * Custom properties.
     */
    public Map<String, String> customProperties;
}
