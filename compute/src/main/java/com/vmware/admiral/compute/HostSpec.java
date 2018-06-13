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

package com.vmware.admiral.compute;

import java.net.URI;
import java.util.List;

import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;

public abstract class HostSpec {

    /**
     * Boolean flag indicating whether the certificate of the host should be accepted or will
     * need confirmation from the user. Typically, when using self-signed certificates, the
     * service will reject the certificate in order for the user to confirm. Default value is
     * false. This is relevant only for hosts with API type of connection (https).
     */
    public boolean acceptCertificate;

    /**
     * Boolean flag indicating whether to validate the host connection when the host is added.
     * If this flag is set to false, adding of the host will fail if the connection cannot be
     * validated. Setting the flag to true will add the container host without validation of the
     * host address. It will ignore acceptCertificate if set, this means that it will also skip
     * the import and store of the ssl trust certificate of the host (if applicable).
     */
    public boolean acceptHostAddress;

    /**
     * (optional) SSL trust for the ping operation
     */
    public SslTrustCertificateState sslTrust;

    public URI uri;

    /**
     * Return true if communication is over a secure channel.
     *
     * @return Default is <code>true</code>.
     */
    public abstract boolean isSecureScheme();

    /**
     * Return tenant links for the host
     */
    public abstract List<String> getHostTenantLinks();

}
