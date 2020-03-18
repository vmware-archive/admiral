/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral;

import static com.vmware.admiral.restmock.MockUtils.resourceToString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.apache.http.HttpEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import com.vmware.admiral.client.HttpClientUtils;
import com.vmware.admiral.restmock.RestMockServer;


public class RestMockServerTests {

    @Test
    public void testSetAndRemoveMock() throws Exception {

        final String mockPath = "/dummy/mock";
        final String payload = resourceToString("/responses/pks/v1/login-success.json");

        RestMockServer mockServer = new RestMockServer();
        mockServer.start();

        // Set mock
        mockServer.createMock("POST", mockPath, payload, 200);

        Request req = Request.Post("https://localhost:" + mockServer.getPort() + mockPath)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .bodyString(payload, ContentType.APPLICATION_JSON);

        HttpEntity entity = HttpClientUtils.execute(req).returnResponse().getEntity();
        assertThat(EntityUtils.toString(entity), is(equalTo(payload)));

        // Remove mock
        mockServer.removeMock(mockPath);
        assertThat(HttpClientUtils.execute(req).returnResponse().getStatusLine().getStatusCode(),
                is(equalTo(404)));

        mockServer.stop();
    }

}
