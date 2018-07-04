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

package com.vmware.admiral.tiller;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitTillerServicesConfig;
import com.vmware.admiral.tiller.TillerService.TillerState;
import com.vmware.xenon.common.FactoryService;

public class TillerServiceTest extends BaseTestCase {

    @Before
    public void setUp() throws Throwable {
        // TODO extract these to a base class for Tiller-services-related tests?
        HostInitTillerServicesConfig.startServices(host);
        waitForServiceAvailability(TillerService.FACTORY_LINK);
    }

    @Test
    public void testTillerService() throws Throwable {
        final String name = "name";
        final String k8sClusterLink = "k8s-cluster-link";
        final String tillerNamespace = "tiller-namespace";
        final String tillerCaLink = "tiller-ca-link";
        final String tillerCredentialsLink = "tiller-credentials-link";

        verifyService(
                FactoryService.create(TillerService.class),
                TillerState.class,
                (prefix, index) -> {
                    TillerState tillerState = new TillerState();
                    tillerState.name = prefix + name + index;
                    tillerState.k8sClusterSelfLink = prefix + k8sClusterLink + index;
                    tillerState.tillerNamespace = prefix + tillerNamespace + index;
                    tillerState.tillerCertificateAuthorityLink = prefix + tillerCaLink + index;
                    tillerState.tillerCredentialsLink = prefix + tillerCredentialsLink + index;
                    return tillerState;
                },
                (prefix, serviceDocument) -> {
                    TillerState tillerState = (TillerState) serviceDocument;
                    assertTrue(tillerState.name.startsWith(prefix + name));
                    assertTrue(tillerState.k8sClusterSelfLink.startsWith(prefix + k8sClusterLink));
                    assertTrue(tillerState.tillerNamespace.startsWith(prefix + tillerNamespace));
                    assertTrue(tillerState.tillerCertificateAuthorityLink
                            .startsWith(prefix + tillerCaLink));
                    assertTrue(tillerState.tillerCredentialsLink
                            .startsWith(prefix + tillerCredentialsLink));
                });
    }
}
