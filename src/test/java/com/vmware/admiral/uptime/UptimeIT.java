/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 */

package com.vmware.admiral.uptime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod.GET;
import static com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod.POST;
import static com.vmware.xenon.services.common.authn.BasicAuthenticationUtils.constructBasicAuth;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.ODataFactoryQueryResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class UptimeIT extends BaseIntegrationSupportIT {

    private static final String USERNAME = "administrator@xenon.local";
    private static final String PASSWORD = "VMware1!";

    private static final String APPLICATION = "app-2-alpine-1-network.yaml";

    private static final int RANDOM_MAX_LIMIT =
            Math.max(Integer.getInteger("RANDOM_MAX_LIMIT", 10), 10);

    /**
     * List with currently supported operations for the test. Each operation has relative weight.
     * On each run of the test a random operation is executed. Higher the weight higher the chance
     * to be executed.
     */
    enum Operations {

        PROVISION_APPLICATION(40),
        DELETE_APPLICATION(10),
        STOP_APPLICATION(10);

        Operations(int weight) {
            this.weight = weight;
        }

        private int weight;

        /**
         * Returns random operation counting the weights.
         */
        static Operations randomOperation() {
            List<Operations> list = new ArrayList<>();
            for (Operations o : Operations.values()) {
                for (int i = 0; i < o.weight; i++) {
                    list.add(o);
                }
            }
            // double random :)
            Collections.shuffle(list);
            return list.get((int) Math.floor(Math.random() * list.size()));
        }
    }

    @Test
    public void test() throws Exception {
        Operations op = Operations.randomOperation();
        logger.info("Executing operation: %s", op.name());

        login(USERNAME, PASSWORD);

        switch (op) {
        case PROVISION_APPLICATION:
            provisionApplications();
            break;
        case DELETE_APPLICATION:
            deleteApplications();
            break;
        case STOP_APPLICATION:
            stopApplications();
            break;
        }
    }

    private void provisionApplications() throws Exception {
        String templateId = importTemplate(APPLICATION);
        logger.info("Imported composite description id = %s", templateId);

        int n = (int) (Math.random() * RANDOM_MAX_LIMIT + 5);
        logger.info("Start provisioning %d applications", n);
        for (int i = 0; i < n; i++) {
            RequestBrokerState request = requestTemplate(templateId, true);
            StringBuilder sb = new StringBuilder();
            sb.append("\n(App: ").append(i).append(") - Provisioned resources :");
            for (String resourceLink : request.resourceLinks) {
                sb.append("\n\tresourceLink = ").append(resourceLink);
                CompositeComponent cc = getDocument(resourceLink, CompositeComponent.class);
                for (String componentLink : cc.componentLinks) {
                    sb.append("\n\t\tcomponentLink = ").append(componentLink);
                }
            }
            logger.info(sb.toString());
        }
    }

    private void deleteApplications() throws Exception {
        int n = (int) (Math.random() * RANDOM_MAX_LIMIT + 2);
        logger.info("Start deleting %d applications", n);
        List<String> links = getApplications(n);

        if (links.size() == 0) {
            logger.info("No composite components. Exit.");
            return;
        }

        RequestBrokerState request = new RequestBrokerState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceLinks = new HashSet<>(links);
        logger.info("Deleting composite components %s", request.resourceLinks);

        HttpResponse response = SimpleHttpsClient.execute(
                POST, getUri(RequestBrokerFactoryService.SELF_LINK), Utils.toJson(request));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.statusCode);

        request = Utils.fromJson(response.responseBody, RequestBrokerState.class);

        waitForTaskToComplete(request.documentSelfLink);
        request = getDocument(request.documentSelfLink, RequestBrokerState.class);

        assertNull(request.taskInfo.failure);
        assertEquals(TaskState.TaskStage.FINISHED, request.taskInfo.stage);
        assertEquals(RequestBrokerState.SubStage.COMPLETED, request.taskSubStage);
    }

    private void stopApplications() throws Exception {
        int n = (int) (Math.random() * RANDOM_MAX_LIMIT + 2);
        logger.info("Start stopping %d applications", n);
        List<String> links = getApplications(n);

        if (links.size() == 0) {
            logger.info("No composite components. Exit.");
            return;
        }

        RequestBrokerState request = new RequestBrokerState();
        request.operation = ContainerOperationType.STOP.id;
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceLinks = new HashSet<>(links);
        logger.info("Stopping composite components %s", request.resourceLinks);

        HttpResponse response = SimpleHttpsClient.execute(
                POST, getUri(RequestBrokerFactoryService.SELF_LINK), Utils.toJson(request));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.statusCode);

        request = Utils.fromJson(response.responseBody, RequestBrokerState.class);

        waitForTaskToComplete(request.documentSelfLink);
        request = getDocument(request.documentSelfLink, RequestBrokerState.class);

        assertNull(request.taskInfo.failure);
        assertEquals(TaskState.TaskStage.FINISHED, request.taskInfo.stage);
        assertEquals(RequestBrokerState.SubStage.COMPLETED, request.taskSubStage);
    }

    private void login(String username, String password) throws Exception {
        String uri = getUri(BasicAuthenticationService.SELF_LINK);
        logger.info("Logging to %s", uri);

        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.AUTHORIZATION_HEADER, constructBasicAuth(username, password));
        headers.put(Operation.CONTENT_TYPE_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);

        String body = "{\"requestType\":\"LOGIN\"}";
        HttpResponse response = SimpleHttpsClient.execute(POST, uri, body, headers, null);

        if (response == null || response.statusCode != HttpStatus.SC_OK) {
            Thread.sleep(5000);
            response = SimpleHttpsClient.execute(POST, uri, body, headers, null);
        }

        assertNotNull(response);
        assertNotNull(response.headers);
        assertNotNull(response.headers.get(Operation.REQUEST_AUTH_TOKEN_HEADER));

        String token = response.headers.get(Operation.REQUEST_AUTH_TOKEN_HEADER).get(0);
        SimpleHttpsClient.setAuthToken(token);
        logger.info("Auth token = %s", token);
    }

    private List<String> getApplications(int n) throws Exception {
        String uri = getUri(CompositeComponentFactoryService.SELF_LINK, "?$limit=" + n);

        HttpResponse r = SimpleHttpsClient.execute(GET, uri, null);
        assertNotNull(r);
        ODataFactoryQueryResult qr = Utils.fromJson(r.responseBody, ODataFactoryQueryResult.class);
        assertNotNull(qr);
        assertNotNull(qr.documentLinks);
        logger.info("returning %d composite components", qr.documentLinks.size());
        return qr.documentLinks;
    }

    private String importTemplate(String filePath) throws Exception {
        String uri = getUri(CompositeDescriptionContentService.SELF_LINK);

        String body = CommonTestStateFactory.getFileContent(filePath);
        HashMap<String, String> headers = new HashMap<>();
        headers.put(Operation.CONTENT_TYPE_HEADER, MEDIA_TYPE_APPLICATION_YAML);
        HttpResponse response =
                SimpleHttpsClient.execute(POST, uri, body, headers, null);

        assertNotNull(response);
        assertNotNull(response.headers);

        String location = response.headers.get(Operation.LOCATION_HEADER).get(0);
        assertNotNull("Missing location header", location);
        return URI.create(location).getPath();
    }

    protected RequestBrokerState requestTemplate(String resourceDescLink, boolean shouldWait)
            throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        request.resourceDescriptionLink = resourceDescLink;
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();

        HttpResponse response = SimpleHttpsClient.execute(
                POST, getUri(RequestBrokerFactoryService.SELF_LINK), Utils.toJson(request));

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.statusCode);

        request = Utils.fromJson(response.responseBody, RequestBrokerState.class);

        if (shouldWait) {
            waitForTaskToComplete(request.documentSelfLink);
            request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        }

        return request;
    }

    private String getUri(String... paths) {
        return URI.create(getBaseUrl() + buildServiceUri(paths)).toString();
    }

}
