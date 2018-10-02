/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.host.interceptor.AuthCredentialsInterceptor;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsOperationProcessingChain;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AuthCredentialsInterceptorTest extends BaseTestCase {

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() throws Throwable {
        // wait for needed services
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);

        // common setup
        System.clearProperty(EncryptionUtils.ENCRYPTION_KEY);
        System.clearProperty(EncryptionUtils.INIT_KEY_IF_MISSING);

        EncryptionUtils.initEncryptionService();
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        AuthCredentialsInterceptor.register(registry);
    }

    @Test
    public void testDeleteCredentialsInUseShouldFail() throws Throwable {

        // start other services
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        // wait for other needed services
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        AuthCredentialsServiceState credentials = createCredentials("username", "password", false);
        ResourcePoolState placementZone = createPlacementZone("default-test-placement-zone");
        ComputeState computeState = createComputeState(placementZone, credentials);

        Operation delete;

        // try to delete the credentials. This should fail!
        delete = Operation.createDelete(UriUtils.buildUri(host, credentials.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            verifyExceptionMessage(e.getMessage(),
                                    AuthCredentialsOperationProcessingChain.CREDENTIALS_IN_USE_MESSAGE);
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when deleting credentials that are in use"));
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();

        credentials = getDocumentNoWait(AuthCredentialsServiceState.class,
                credentials.documentSelfLink);
        assertNotNull(credentials);

        // try to delete the compute first. This should work!
        delete = Operation.createDelete(UriUtils.buildUri(host, computeState.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        host.completeIteration();
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();

        // try to delete the credentials again. This should work!
        delete = Operation.createDelete(UriUtils.buildUri(host, credentials.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        host.completeIteration();
                    }
                });
        host.testStart(1);
        host.send(delete);
        host.testWait();

        credentials = getDocumentNoWait(AuthCredentialsServiceState.class,
                credentials.documentSelfLink);
        assertNull(credentials);
    }

    @Test
    public void testPlainTextCredentials() throws Throwable {

        // do NOT init EncryptionUtils

        assertNull("ENCRYPTION_KEY env variable should be null",
                System.getProperty(EncryptionUtils.ENCRYPTION_KEY));
        assertNull("INIT_KEY_IF_MISSING env variable should be null",
                System.getProperty(EncryptionUtils.INIT_KEY_IF_MISSING));

        AuthCredentialsServiceState credentials = createCredentials("username", "password", false);

        assertEquals("username mismatch", "username", credentials.userEmail);
        assertNotNull("privateKey should not be null", credentials.privateKey);
        assertFalse("private key should not be encrypted",
                credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String publicKey = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----";

        credentials = createCredentialsWithKeys(publicKey,
                "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----");

        assertEquals("public key mismatch", publicKey, credentials.publicKey);
        assertNotNull("privateKey should not be null", credentials.privateKey);
        assertFalse("private key should not be encrypted",
                credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
    }

    @Test
    public void testEncryptedCredentials() throws Throwable {

        // init EncryptionUtils

        File keyFile = Paths.get(folder.newFolder().getPath(), "encryption.key").toFile();
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();

        AuthCredentialsServiceState credentials = createCredentials("username", "password", false);

        assertEquals("username", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        String publicKey = "-----BEGIN CERTIFICATE-----\nABC\n-----END CERTIFICATE-----";

        credentials = createCredentialsWithKeys(publicKey,
                "-----BEGIN PRIVATE KEY-----\nDEF\n-----END PRIVATE KEY-----");

        assertEquals(publicKey, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        // if the private key is (sent) already encrypted, it's not re-encrypted

        String encryptedOnce = credentials.privateKey;

        String publicKeyNew = "-----BEGIN CERTIFICATE-----\nGHI\n-----END CERTIFICATE-----";

        credentials.publicKey = publicKeyNew;

        credentials = doPut(credentials);

        assertEquals(publicKeyNew, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals(encryptedOnce, credentials.privateKey);

        // if the private key has changed, it's re-encrypted

        credentials.privateKey = "-----BEGIN PRIVATE KEY-----\nJKL\n-----END PRIVATE KEY-----";

        credentials = doPut(credentials);

        assertEquals(publicKeyNew, credentials.publicKey);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertNotEquals(encryptedOnce, credentials.privateKey);
    }

    @Test
    public void testPlainTextSystemCredentials() throws Throwable {

        // init EncryptionUtils

        File keyFile = Paths.get(folder.newFolder().getPath(), "encryption.key").toFile();
        System.setProperty(EncryptionUtils.ENCRYPTION_KEY, keyFile.getPath());
        System.setProperty(EncryptionUtils.INIT_KEY_IF_MISSING, "true");
        EncryptionUtils.initEncryptionService();

        AuthCredentialsServiceState credentials = createCredentials("username", "password", true);

        assertEquals("username", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals("password", credentials.privateKey);

        credentials = createCredentials("username2", "password2", false);

        assertEquals("username2", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertTrue(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));

        // like AuthBootstrapService does

        AuthCredentialsServiceState credentialsPatch = new AuthCredentialsServiceState();
        credentialsPatch.privateKey = "password2";
        credentialsPatch.customProperties = new HashMap<>();
        credentialsPatch.customProperties.put(AuthConfigProvider.PROPERTY_SCOPE,
                AuthConfigProvider.CredentialsScope.SYSTEM.toString());

        credentials = doPatch(credentialsPatch, credentials.documentSelfLink);

        assertEquals("username2", credentials.userEmail);
        assertNotNull(credentials.privateKey);
        assertFalse(credentials.privateKey.startsWith(EncryptionUtils.ENCRYPTION_PREFIX));
        assertEquals("password2", credentials.privateKey);
    }

    protected AuthCredentialsServiceState createCredentials(String username, String password,
            boolean isSystem) throws Throwable {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.userEmail = username;
        credentials.privateKey = password;
        credentials.type = AuthCredentialsType.Password.toString();
        if (isSystem) {
            credentials.customProperties = new HashMap<>();
            credentials.customProperties.put(AuthConfigProvider.PROPERTY_SCOPE,
                    AuthConfigProvider.CredentialsScope.SYSTEM.toString());
        }
        return getOrCreateDocument(credentials, AuthCredentialsService.FACTORY_LINK);
    }

    protected AuthCredentialsServiceState createCredentialsWithKeys(String publicKey,
            String privateKey) throws Throwable {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.publicKey = publicKey;
        credentials.privateKey = privateKey;
        credentials.type = AuthCredentialsType.PublicKey.toString();
        return getOrCreateDocument(credentials, AuthCredentialsService.FACTORY_LINK);
    }

    private ResourcePoolState createResourcePoolState(String placementZoneName) {
        assertNotNull(placementZoneName);

        ResourcePoolState placementZone = new ResourcePoolState();
        placementZone.id = placementZoneName;
        placementZone.name = placementZoneName;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;
        placementZone.customProperties = new HashMap<>();
        placementZone.customProperties.put(
                PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                PlacementZoneType.DOCKER.toString());
        return placementZone;
    }

    private ResourcePoolState createPlacementZone(String placementZoneName) throws Throwable {
        ResourcePoolState resourcePoolState = createResourcePoolState(placementZoneName);
        return doPost(resourcePoolState, ResourcePoolService.FACTORY_LINK);
    }

    private ComputeState createComputeState(ResourcePoolState placementZone,
            AuthCredentialsServiceState credentials) throws Throwable {
        assertNotNull(placementZone);
        assertNotNull(credentials);

        ComputeState computeState = new ComputeState();
        computeState.address = "no-address";
        computeState.descriptionLink = "no-description-link";
        computeState.resourcePoolLink = placementZone.documentSelfLink;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.DOCKER.toString());
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                credentials.documentSelfLink);
        return doPost(computeState, ComputeService.FACTORY_LINK);
    }

    private void verifyExceptionMessage(String message, String expected) {
        if (!message.equals(expected)) {
            String errorMessage = String.format("Expected error '%s' but was '%s'", expected,
                    message);
            throw new IllegalStateException(errorMessage);
        }
    }
}
