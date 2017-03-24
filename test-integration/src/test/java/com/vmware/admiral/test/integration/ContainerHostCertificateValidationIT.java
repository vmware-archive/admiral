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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.HttpURLConnection;

import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ContainerHostCertificateValidationIT extends BaseProvisioningOnCoreOsIT {

    @Test
    public void testAuthCredentialsValidationForHost() throws Exception {

        // create credentials to validate
        AuthCredentialsServiceState dockerHostAuthCredentials = createCredentials();
        assertNotNull("Failed to create host credentials", dockerHostAuthCredentials);

        // create failing credentials
        AuthCredentialsServiceState invalidCredentials = createInvalidCredentials();

        HttpResponse creds = SimpleHttpsClient.execute(HttpMethod.POST,
                getBaseUrl() + buildServiceUri(AuthCredentialsService.FACTORY_LINK),
                Utils.toJson(invalidCredentials));
        invalidCredentials = Utils.fromJson(creds.responseBody, AuthCredentialsServiceState.class);

        // build the host to validate
        ContainerHostSpec hostSpec = createHost(dockerHostAuthCredentials);

        // should succeed - correct certs
        String targetUrl = String.format("%s%s?%s=true", getBaseUrl(),
                buildServiceUri(ContainerHostService.SELF_LINK),
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                targetUrl, Utils.toJson(hostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add host failed with status code: "
                    + httpResponse.statusCode);
        }

        // should fail - wrong certs
        hostSpec.hostState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                invalidCredentials.documentSelfLink);

        try {
            httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                    targetUrl, Utils.toJson(hostSpec));
            fail("Validating host with wrong credentials should fail.");
        } catch (IllegalArgumentException ex) {
            // expected - validating host with wrong credentials should fail.
        }
    }

    private ContainerHostSpec createHost(AuthCredentialsServiceState dockerHostAuthCredentials) {
        String hostPort = getTestRequiredProp(
                "docker.host.port." + ContainerHostService.DockerAdapterType.API.name());
        String credLink = dockerHostAuthCredentials.documentSelfLink;

        ComputeState compute = IntegratonTestStateFactory.createDockerComputeHost();

        compute.address = getTestRequiredProp("docker.host.address");
        compute.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, hostPort);

        compute.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);

        ContainerHostSpec hostSpec = new ContainerHostSpec();
        hostSpec.hostState = compute;
        hostSpec.acceptCertificate = true;
        return hostSpec;
    }

    private AuthCredentialsServiceState createCredentials() throws Exception {
        AuthCredentialsServiceState dockerHostAuthCredentials = IntegratonTestStateFactory
                .createAuthCredentials(true);
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        dockerHostAuthCredentials.privateKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.key.file"));
        dockerHostAuthCredentials.publicKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.cert.file"));

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                dockerHostAuthCredentials);
        return dockerHostAuthCredentials;
    }

    private AuthCredentialsServiceState createInvalidCredentials() throws Exception {
        AuthCredentialsServiceState dockerHostAuthCredentials = IntegratonTestStateFactory
                .createAuthCredentials(true);
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        dockerHostAuthCredentials.privateKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.key.file"));
        // Choose a certificate that is parseable, but invalid for the provided host or expired
        // This way the actual connection to the host will be tested.
        dockerHostAuthCredentials.publicKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.cert.file.invalid"));

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                dockerHostAuthCredentials);
        return dockerHostAuthCredentials;
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }

}
