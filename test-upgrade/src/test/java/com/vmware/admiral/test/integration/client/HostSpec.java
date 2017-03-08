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

import javax.xml.bind.annotation.XmlTransient;

@XmlTransient
public abstract class HostSpec<T extends TenantedServiceDocument> {
    /** The state for the host to be created or validated. */
    public T hostState;

    /**
     * Boolean flag indicating whether the certificate of the host should be accepted or will need
     * confirmation from the user. Typically, when using self-signed certificates, the service will
     * reject the certificate in order for the user to confirm. Default value is false. This is
     * relevant only for hosts with API type of connection (https).
     */
    public boolean acceptCertificate;

    /**
     * Boolean flag indicating whether to validate the host connection when the host is added. If
     * this flag is set to false, adding of the host will fail if the connection cannot be
     * validated. Setting the flag to true will add the container host without validation of the
     * host address.
     */
    public boolean acceptHostAddress;

    /**
     * (optional) SSL trust for the ping operation
     */
    public SslTrustCertificateState sslTrust;
}
