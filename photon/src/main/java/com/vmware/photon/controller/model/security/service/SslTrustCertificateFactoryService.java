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

package com.vmware.photon.controller.model.security.service;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public class SslTrustCertificateFactoryService extends FactoryService {

    public static final String SELF_LINK = SslTrustCertificateService.FACTORY_LINK;

    public SslTrustCertificateFactoryService() {
        super(SslTrustCertificateState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new SslTrustCertificateService();
    }

    /**
     * Override the handlePost method to set the documentSelfLink. We don't want to have multiple
     * certificate states with the same certificate, so we build the documentSelfLink ourselves
     *
     * @param op
     */
    @Override
    public void handlePost(Operation op) {
        if (op.isSynchronize()) {
            op.complete();
            return;
        }
        if (op.hasBody()) {
            SslTrustCertificateState body = (SslTrustCertificateState) op.getBody(this.stateType);
            if (body == null) {
                op.fail(new IllegalArgumentException("structured body is required"));
                return;
            }

            if (body.documentSourceLink != null) {
                op.fail(new IllegalArgumentException("clone request not supported"));
                return;
            }

            body.documentSelfLink = generateSelfLink(body);
            op.setBody(body);
            op.complete();
        } else {
            op.fail(new IllegalArgumentException("body is required"));
        }
    }

    public static String generateSelfLink(SslTrustCertificateState body) {
        AssertUtil.assertNotEmpty(body.certificate, "certificate");

        return CertificateUtil.generatePureFingerPrint(
                CertificateUtil.createCertificateChain(body.certificate));
    }

}
