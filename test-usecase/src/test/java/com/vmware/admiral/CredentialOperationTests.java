/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import static com.vmware.admiral.AdmiralClientSuite.createLocalhostClient;
import static com.vmware.admiral.restmock.MockUtils.resourceToString;

import com.google.gson.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.client.AdmiralClient;
import com.vmware.admiral.client.AdmiralClientException;

public class CredentialOperationTests {

    private static final String ADMIN_USER_NAME = "fritz@admiral.com";
    private static final String ADMIN_USER_PASSWORD = "Password1!";
    private static final String REGULAR_USER_NAME = "tony@admiral.com";
    private static final String REGULAR_USER_PASSWORD = ADMIN_USER_PASSWORD;

    private static boolean isAdmiralStartedLocally;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        if(!AdmiralClientSuite.isAdmiralRunning()) {
            AdmiralClientSuite.setUpAdmiralProcess();
            isAdmiralStartedLocally = true;
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if(isAdmiralStartedLocally && AdmiralClientSuite.isAdmiralRunning()) {
            AdmiralClientSuite.tearDownAdmiralProcess();
        }
    }

    @Test
    public void adminCreatesRetrievesDestroysPasswordCredentials() throws Exception {

        // Create client
        AdmiralClient client = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);

        // Create
        String credSelfLink = client.createUsernameCredential(
                "Test Credentials", "dummy-user", "dummy-password");

        assertThat("Object created", credSelfLink, is(notNullValue()));

        // Retrieve
        JsonObject json = client.getResource(credSelfLink);

        assertThat("Object retrieved", json, is(notNullValue()));
        assertThat(json.get("type").getAsString(), is(equalTo("Password")));
        assertThat(json.get("userEmail").getAsString(), is(equalTo("dummy-user")));
        assertThat(json.get("privateKey").getAsString(), is(equalTo("dummy-password")));

        // Delete
        boolean deleted = client.deleteResource(credSelfLink);
        assertThat("Object deleted", deleted, is(true));
    }

    @Test
    public void regularUserCannotCreatePasswordCredentials() throws Exception {

        // Create client
        AdmiralClient client = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Try to create
        try {
            client.createUsernameCredential(
                    "Test Credentials", "dummy-user", "dummy-password");
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions", e.getStatus(), is(equalTo(403)));
        }
    }

    @Test
    public void adminCreatesCertificate() throws Exception {

        // Create client
        AdmiralClient client = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);

        // Create
        String certBody = resourceToString("/misc/valid-certificate");
        String certSelfLink = client.createCertificate(certBody);
        assertThat(certSelfLink, is(not(emptyString())));

        // Retrieve
        JsonObject json = client.getResource(certSelfLink);

        assertThat("Object retrieved", json, is(notNullValue()));
        assertThat(json.get("certificate").getAsString(), is(equalTo(certBody)));

        // Delete
        boolean deleted = client.deleteResource(certSelfLink);
        assertThat("Object deleted", deleted, is(true));

        try {
            client.getResource(certSelfLink);
        } catch (AdmiralClientException e) {
            assertThat("Status 404 returned", e.getStatus(), is(equalTo(404)));
        }
    }

    @Test
    public void regularUserCannotCreateCertificate() throws Exception {

        // Create client
        AdmiralClient client = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Try to create
        try {
            client.createCertificate(resourceToString("/misc/valid-certificate"));
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions", e.getStatus(), is(equalTo(403)));
        }
    }
}
