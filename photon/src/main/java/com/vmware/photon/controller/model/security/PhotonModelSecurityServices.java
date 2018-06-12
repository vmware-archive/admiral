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

package com.vmware.photon.controller.model.security;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateFactoryService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.ServiceHost;

/**
 * Helper class that starts all the photon model security related services
 */
public class PhotonModelSecurityServices {

    public static final ServiceMetadata[] SERVICES_METADATA = {
            factoryService(SslTrustCertificateService.class,
                    SslTrustCertificateFactoryService::new)
    };

    public static final String[] LINKS = {
            SslTrustCertificateService.FACTORY_LINK };

    public static void startServices(ServiceHost host) throws Throwable {
        host.startFactory(SslTrustCertificateService.class, SslTrustCertificateFactoryService::new);
    }
}
