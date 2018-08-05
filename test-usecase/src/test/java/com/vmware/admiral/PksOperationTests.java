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

import static java.text.MessageFormat.format;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import static com.vmware.admiral.AdmiralClientSuite.createLocalhostClient;
import static com.vmware.admiral.restmock.MockUtils.resourceToString;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.client.AdmiralClient;
import com.vmware.admiral.client.AdmiralClient.UserRole;
import com.vmware.admiral.client.AdmiralClientException;
import com.vmware.admiral.restmock.RestMockServer;

@RunWith(Parameterized.class)
public class PksOperationTests {

    private static final String ADMIN_USER_NAME = "fritz@admiral.com";
    private static final String ADMIN_USER_PASSWORD = "Password1!";
    private static final String REGULAR_USER_NAME = "tony@admiral.com";
    private static final String REGULAR_USER_PASSWORD = ADMIN_USER_PASSWORD;

    private static boolean isAdmiralStartedLocally;
    private static RestMockServer pks;
    private static String uaaAddress, pksAddress;
    private String pksVersion;

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{"v1"}});
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        if(!AdmiralClientSuite.isAdmiralRunning()) {
            AdmiralClientSuite.setUpAdmiralProcess();
            isAdmiralStartedLocally = true;
        }

        pks = new RestMockServer();
        uaaAddress = "https://localhost:" + pks.getPort();
        pksAddress = uaaAddress;
        pks.start();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if(pks != null) {
            pks.stop();
        }

        if(isAdmiralStartedLocally && AdmiralClientSuite.isAdmiralRunning()) {
            AdmiralClientSuite.tearDownAdmiralProcess();
        }
    }

    public PksOperationTests(String pksVersion) throws Exception {
        this.pksVersion = pksVersion;

        // Mocks that will typically not change
        pks.createMock("POST", "/oauth/token",
                resourceToString(completePath("login-success.json")), 200);
        pks.createMock("GET", "/v1/plans",
                resourceToString(completePath("plans-success.json")), 200);
    }

    private String completePath(String fileName) {
        return format("/responses/pks/{0}/{1}", pksVersion, fileName);
    }

    @Test
    public void adminCreatesRetrievesAndDeletesPksEndpoint() throws Exception {

        final String endpointsPath = "/resources/pks/endpoints";

        // Create client
        AdmiralClient client = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);

        // Create credentials
        String credLink = client.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Validate PKS endpoint
        final String epName = "Mocked PKS Endpoint";
        final String epDescr = "This is a mocked PKS endpoint";

        String cert = client.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, true);
        assertThat(cert, is(not(emptyString())));

        // Add certificate
        String certLink = client.createCertificate(cert);
        assertThat(certLink, is(not(nullValue())));

        // Create PKS endpoint
        String endpointLink = client.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, false);
        assertThat("PKS endpoint created by admin", endpointLink, is(not(emptyString())));

        // Retrieve endpoint
        JsonArray resources = client.getResources(endpointsPath);
        assertThat("PKS endpoint can be retrieved", resources, iterableWithSize(1));
        assertThat("PKS enpoint is correct", resources.get(0).getAsString(), is(equalTo(endpointLink)));

        // Delete endpoint
        boolean deleted = client.deleteResource(endpointLink);
        assertThat("PKS endpoint deleted", deleted, is(true));
        assertThat("PKS endpoint list is empty",
                client.getResources(endpointsPath), iterableWithSize(0));

        // Delete credentials
        deleted = client.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));

        // Delete certificate
        deleted = client.deleteResource(certLink);
        assertThat("Certificate deleted", deleted, is(true));
    }

    @Test
    public void regularUserCannotCreatePksEndpoint() throws Exception {

        // Create clients
        AdmiralClient adminClient = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
        AdmiralClient regularUserClient = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Create credentials with admin
        String credLink = adminClient.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Try to add PKS endpoint with regular user
        try {
            regularUserClient.validateOrCreatePksEndpoint("Mocked PKS Endpoint",
                    uaaAddress, pksAddress, credLink, "This is a mocked PKS endpoint", true);
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions", e.getStatus(), is(equalTo(403)));
        }

        // Delete credentials
        boolean deleted = adminClient.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));
    }

    @Test
    public void regularUserCannotRetrieveOrDeleteExistingPksEndpoint() throws Exception {

        // Create clients
        AdmiralClient adminClient = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
        AdmiralClient regularUserClient = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Create credentials with admin
        String credLink = adminClient.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Validate PKS endpoint
        final String epName = "Mocked PKS Endpoint";
        final String epDescr = "This is a mocked PKS endpoint";

        String cert = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, true);
        assertThat(cert, is(not(emptyString())));

        // Add certificate
        String certLink = adminClient.createCertificate(cert);
        assertThat(certLink, is(not(nullValue())));

        // Create PKS endpoint
        String endpointLink = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, false);
        assertThat("PKS endpoint created by admin", endpointLink, is(not(emptyString())));

        // Try to retrieve endpoint with regular user
        try {
            regularUserClient.getResource(endpointLink);
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions", e.getStatus(), is(equalTo(403)));
        }

        // Try to delete endpoint with regular user
        try {
            regularUserClient.deleteResource(endpointLink);
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions", e.getStatus(), is(equalTo(403)));
        }

        // Delete endpoint
        boolean deleted = adminClient.deleteResource(endpointLink);
        assertThat("PKS endpoint deleted", deleted, is(true));

        // Delete credentials
        deleted = adminClient.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));

        // Delete certificate
        deleted = adminClient.deleteResource(certLink);
        assertThat("Certificate deleted", deleted, is(true));
    }

    @Test
    public void adminRetrievesPksClusters() throws Exception {

        final String pksClustersPath = "/v1/clusters";
        final String endpointsPath = "/resources/pks/endpoints";

        // Prepare mock
        pks.createMock("GET", pksClustersPath,
                resourceToString(completePath("clusters-success.json")), 200);

        // Create client
        AdmiralClient client = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);

        // Create credentials
        String credLink = client.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Validate PKS endpoint
        final String epName = "Mocked PKS Endpoint";
        final String epDescr = "This is a mocked PKS endpoint";

        String cert = client.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, true);
        assertThat(cert, is(not(emptyString())));

        // Add certificate
        String certLink = client.createCertificate(cert);
        assertThat(certLink, is(not(nullValue())));

        // Create PKS endpoint
        String endpointLink = client.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, false);
        assertThat("PKS endpoint created by admin", endpointLink, is(not(emptyString())));

        // Get clusters
        List<JsonObject> clusters = client.getPksClusters(endpointLink);
        assertThat("Corrent number of clusters retrieved", clusters,
                allOf(is(not(empty())), hasSize(4)));

        // Delete endpoint
        boolean deleted = client.deleteResource(endpointLink);
        assertThat("PKS endpoint deleted", deleted, is(true));
        assertThat("PKS endpoint list is empty",
                client.getResources(endpointsPath), iterableWithSize(0));

        // Delete credentials
        deleted = client.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));

        // Delete certificate
        deleted = client.deleteResource(certLink);
        assertThat("Certificate deleted", deleted, is(true));

        // Remove mock
        pks.removeMock(pksClustersPath);
    }

    @Test
    public void regularUserCannotRetrievePksClusters() throws Exception {

        final String endpointsPath = "/resources/pks/endpoints";

        // Create client
        AdmiralClient adminClient = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
        AdmiralClient regularUserClient = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Create credentials
        String credLink = adminClient.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Validate and add PKS endpoint
        final String epName = "Mocked PKS Endpoint";
        final String epDescr = "This is a mocked PKS endpoint";

        String cert = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, true);
        assertThat(cert, is(not(emptyString())));

        // Add certificate
        String certLink = adminClient.createCertificate(cert);
        assertThat(certLink, is(not(nullValue())));

        // Create PKS endpoint
        String endpointLink = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, false);
        assertThat("PKS endpoint created by admin", endpointLink, is(not(emptyString())));

        // Get clusters
        try {
            regularUserClient.getPksClusters(endpointLink);
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions",
                    e.getStatus(), is(equalTo(403)));
        }

        // Delete endpoint
        boolean deleted = adminClient.deleteResource(endpointLink);
        assertThat("PKS endpoint deleted", deleted, is(true));
        assertThat("PKS endpoint list is empty",
                adminClient.getResources(endpointsPath), iterableWithSize(0));

        // Delete credentials
        deleted = adminClient.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));

        // Delete certificate
        deleted = adminClient.deleteResource(certLink);
        assertThat("Certificate deleted", deleted, is(true));
    }

    //@Ignore("Blocked by https://jira.eng.vmware.com/browse/VBV-2178 - "
    //        + "\"java.lang.Long cannot be cast to java.lang.String\" when adding a PKS cluster in master")
    @Test
    public void regularUserCannotRetrievePksClustersInAnotherProject() throws Exception {

        final String pksClustersMockPath = "/v1/clusters";
        final String demoClusterBindsMockPath =
                format("{0}/{1}/binds", pksClustersMockPath, "demo-cluster");
        final String demoBindsMockPath = format("{0}/{1}/binds", pksClustersMockPath, "Demo");
        final String apiVersionMockPath = "/v1.24";
        final String healtzMockPath = "/healthz";
        final String endpointsPath = "/resources/pks/endpoints";

        // Prepare mock
        pks.createMock("GET", pksClustersMockPath,
                resourceToString(completePath("clusters-success-parametrized.json"))
                    .replaceAll("<host>", "localhost")
                    .replaceAll("<port>", String.valueOf(pks.getPort())), 200);
        pks.createMock("POST", demoClusterBindsMockPath,
                resourceToString(completePath("binds-success.json")), 200);
        pks.createMock("POST", demoBindsMockPath,
                resourceToString(completePath("binds-success.json")), 200);

        // TODO Check if this call should happen at all. Might be a bug.
        pks.createMock("GET", apiVersionMockPath, StringUtils.EMPTY, 200);

        pks.createMock("GET", healtzMockPath, StringUtils.EMPTY, 200);

        // Create client
        AdmiralClient adminClient = createLocalhostClient(ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
        AdmiralClient regularUserClient = createLocalhostClient(REGULAR_USER_NAME, REGULAR_USER_PASSWORD);

        // Create credentials
        String credLink = adminClient.createUsernameCredential("Dummy Credentials", "dummy", "dummy");
        assertThat("Credential created", credLink, is(not(emptyString())));

        // Validate and add PKS endpoint
        final String epName = "Mocked PKS Endpoint";
        final String epDescr = "This is a mocked PKS endpoint";

        String cert = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, true);
        assertThat(cert, is(not(emptyString())));

        // Add certificate
        String certLink = adminClient.createCertificate(cert);
        assertThat(certLink, is(not(nullValue())));

        // Create PKS endpoint
        String endpointLink = adminClient.validateOrCreatePksEndpoint(epName,
                uaaAddress, pksAddress, credLink, epDescr, false);
        assertThat("PKS endpoint created by admin", endpointLink, is(not(emptyString())));

        // Create project A
        String projectLinkA = adminClient.createProject("project-a", "This is a dummy project", false);
        assertThat("Project created", projectLinkA, is(not(emptyString())));

        // Create project B
        String projectLinkB = adminClient.createProject("project-b", "This is a dummy project", false);
        assertThat("Project created", projectLinkB, is(not(emptyString())));

        // Assign regular user to project
        adminClient.addUserToProject(REGULAR_USER_NAME, UserRole.MEMBER, projectLinkA);
        JsonObject projectA = adminClient.getResource(projectLinkA + "?expand=true");
        assertThat(StreamSupport.stream(projectA.get("members").getAsJsonArray().spliterator(), false)
                .anyMatch(m -> REGULAR_USER_NAME.equals(m.getAsJsonObject().get("id").getAsString())), is(true));

        // Get clusters
        List<JsonObject> clusters = adminClient.getPksClusters(endpointLink);
        assertThat("Corrent number of clusters retrieved", clusters,
                allOf(is(not(empty())), hasSize(4)));

        // Assign clusters to projects
        String clusterLinkA = adminClient.addPksClusterToProject(
                endpointLink, clusters.get(0), projectLinkA);
        assertThat(clusterLinkA, is(not(emptyString())));

        String clusterLinkB = adminClient.addPksClusterToProject(
                endpointLink, clusters.get(1), projectLinkB);
        assertThat(clusterLinkB, is(not(emptyString())));

        // Try to get cluster of other project with regular user
        try {
            regularUserClient.getResources("/resources/clusters", projectLinkB);
        } catch (AdmiralClientException e) {
            assertThat("Status 403 returned on lack of permissions",
                    e.getStatus(), is(equalTo(403)));
        }

        // Delete endpoint
        boolean deleted = adminClient.deleteResource(endpointLink);
        assertThat("PKS endpoint deleted", deleted, is(true));
        assertThat("PKS endpoint list is empty",
                adminClient.getResources(endpointsPath), iterableWithSize(0));

        // Delete credentials
        deleted = adminClient.deleteResource(credLink);
        assertThat("Credentials deleted", deleted, is(true));

        // Verify clusters have been removed with the endpoint
        // TODO Ignored due to https://jira.eng.vmware.com/browse/VBV-2198 as getting a non-existing cluster returns 500 instead of 404
        /*
        try {
            adminClient.getResource(clusterLinkA);
        } catch (AdmiralClientException e) {
            assertThat("Status 404 returned",
                    e.getStatus(), is(equalTo(404)));
        }

        try {
            adminClient.getResource(clusterLinkB);
        } catch (AdmiralClientException e) {
            assertThat("Status 404 returned",
                    e.getStatus(), is(equalTo(404)));
        }
        */

        // Delete projects
        deleted = adminClient.deleteResource(projectLinkA);
        assertThat("Project deleted", deleted, is(true));

        deleted = adminClient.deleteResource(projectLinkB);
        assertThat("Project deleted", deleted, is(true));

        // Delete certificate
        deleted = adminClient.deleteResource(certLink);
        assertThat("Certificate deleted", deleted, is(true));

        // Remove mock
        pks.removeMocks(pksClustersMockPath, demoClusterBindsMockPath,
                demoBindsMockPath, apiVersionMockPath, healtzMockPath);
    }
}
