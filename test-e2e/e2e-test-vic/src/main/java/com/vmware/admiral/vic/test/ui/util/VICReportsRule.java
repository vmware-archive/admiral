/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import com.codeborne.selenide.Configuration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.runner.Description;

import com.vmware.admiral.test.util.BaseRule;
import com.vmware.admiral.test.util.HttpUtils;

public class VICReportsRule extends BaseRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final String REQUEST_STATUS_ENDPOINT = "/request-status?expand=true&$limit=1000";
    private final String EVENT_LOGS_ENDPOINT = "/resources/event-logs?expand=true&$limit=1000";
    private final String TARGET;
    private final String USERNAME;
    private final String PASSWORD;

    private JsonParser parser;
    private Gson gson;

    private boolean succeededTests = false;

    public VICReportsRule(String target, String username, String password) {
        this.TARGET = target;
        this.USERNAME = username;
        this.PASSWORD = password;
    }

    public void succeededTests() {
        this.succeededTests = true;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        getAndWriteReports(description);
    }

    @Override
    protected void succeeded(Description description) {
        if (succeededTests) {
            getAndWriteReports(description);
        }
    }

    public void getAndWriteReports(Description description) {
        String token = VICAuthTokenGetter.getVICAuthToken(TARGET, USERNAME, PASSWORD);
        HttpClient client = HttpUtils.createUnsecureHttpClient(null,
                Arrays.asList(new Header[] { new BasicHeader("x-xenon-auth-token", token) }));
        parser = new JsonParser();
        gson = new GsonBuilder().setPrettyPrinting().create();
        String requestStatusTarget = TARGET + REQUEST_STATUS_ENDPOINT;
        String requestStatusResponse = executeGetRequest(client, requestStatusTarget);
        JsonElement requestsJson = parser.parse(requestStatusResponse);
        String prettyRequests = gson.toJson(requestsJson);

        File requestsFile = new File(
                Configuration.reportsFolder + File.separator
                        + getPathFromClassAndMethodName(description.getClassName(),
                                description.getMethodName())
                        + "request-status.json");
        try {
            FileUtils.writeStringToFile(requestsFile, prettyRequests);
        } catch (IOException e1) {
            LOG.warning(String.format("Could not write requests status to: %s%nError: %s",
                    requestsFile.getAbsolutePath(), e1.getMessage()));
        }
        LOG.info("Requests status: " + requestsFile.getAbsolutePath());

        String eventLogsTarget = TARGET + EVENT_LOGS_ENDPOINT;
        String eventLogsResponse = executeGetRequest(client, eventLogsTarget);
        JsonElement logsJson = parser.parse(eventLogsResponse);
        String prettyLogs = gson.toJson(logsJson);

        File logsFile = new File(
                Configuration.reportsFolder + File.separator
                        + getPathFromClassAndMethodName(description.getClassName(),
                                description.getMethodName())
                        + "events-log.json");
        try {
            FileUtils.writeStringToFile(logsFile, prettyLogs);
        } catch (IOException e1) {
            LOG.warning(String.format("Could not write event logs to: %s%nError: %s",
                    logsFile.getAbsolutePath(), e1.getMessage()));
        }
        LOG.info("Event logs: " + logsFile.getAbsolutePath());
    }

    private String executeGetRequest(HttpClient client, String target) {
        HttpGet requestStatusGet = new HttpGet(target);
        try {
            HttpResponse response = client.execute(requestStatusGet);
            String responseBody = IOUtils.toString(response.getEntity().getContent());
            EntityUtils.consume(response.getEntity());
            return responseBody;
        } catch (IOException e) {
            LOG.warning("Could execute GET to: " + target + "\nError: " + e.getMessage());
            return null;
        }
    }

}
