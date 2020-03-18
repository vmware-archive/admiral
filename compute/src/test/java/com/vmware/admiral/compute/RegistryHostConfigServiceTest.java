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

package com.vmware.admiral.compute;

import static com.vmware.admiral.compute.EndpointCertificateUtil.REQUEST_PARAM_VALIDATE_OPERATION_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class RegistryHostConfigServiceTest extends BaseTestCase {

    @Before
    public void setUp() throws Throwable {
        host.startService(
                Operation.createPost(UriUtils.buildUri(host, RegistryAdapterService.class)),
                new RegistryAdapterService());
        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        RegistryConfigCertificateDistributionService.class)),
                new RegistryConfigCertificateDistributionService());
        host.startService(
                Operation.createPost(UriUtils.buildUri(host, RegistryHostConfigService.class)),
                new RegistryHostConfigService());

        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(RegistryHostConfigService.SELF_LINK);
    }

    @Test
    public void testPlainHttpRegistriesDisabledByDefault() throws Throwable {

        // default behavior - plain HTTP registries not allowed
        ConfigurationUtil.initialize((ConfigurationState[]) null);

        RegistryState registryState = new RegistryState();
        registryState.name = UUID.randomUUID().toString();
        registryState.address = host.getUri().toString();
        assertTrue(registryState.address.startsWith("http://"));
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;

        URI registryUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK);

        // Try to create/update the registry
        try {
            doOperation(registryHostSpec, registryUri, RegistryHostSpec.class, true, Action.PUT);
            fail("Plain HTTP shouldn't be supported!");
        } catch (LocalizableValidationException e) {
            assertEquals("compute.registry.plain.http.not.allowed", e.getErrorMessageCode());
        }

        // Try to validate the registry
        registryUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK,
                UriUtils.buildUriQuery(REQUEST_PARAM_VALIDATE_OPERATION_NAME,
                        Boolean.toString(true)));
        try {
            doOperation(registryHostSpec, registryUri, RegistryHostSpec.class, true, Action.PUT);
            fail("Plain HTTP shouldn't be supported!");
        } catch (LocalizableValidationException e) {
            assertEquals("compute.registry.plain.http.not.allowed", e.getErrorMessageCode());
        }
    }

    @Test
    public void testPlainHttpRegistriesAllowed() throws Throwable {

        // allow plain HTTP registries explicitly
        ConfigurationState config = new ConfigurationState();
        config.key = RegistryHostConfigService.ALLOW_REGISTRY_PLAIN_HTTP_CONNECTION_PROP_NAME;
        config.value = Boolean.toString(true);
        ConfigurationUtil.initialize(config);

        RegistryState registryState = new RegistryState();
        registryState.name = UUID.randomUUID().toString();
        registryState.address = host.getUri().toString();
        assertTrue(registryState.address.startsWith("http://"));
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;

        URI registryUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK);

        // Try to create/update the registry
        try {
            doOperation(registryHostSpec, registryUri, RegistryHostSpec.class, false, Action.PUT);
            fail("Plain HTTP should be supported!");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Ping attempts failed with errors"));
        }

        // Try to validate the registry
        registryUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK,
                UriUtils.buildUriQuery(REQUEST_PARAM_VALIDATE_OPERATION_NAME,
                        Boolean.toString(true)));
        try {
            doOperation(registryHostSpec, registryUri, RegistryHostSpec.class, false, Action.PUT);
            fail("Plain HTTP should be supported!");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Ping attempts failed with errors"));
        }
    }

}
