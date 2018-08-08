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

package com.vmware.admiral.test.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.codeborne.selenide.Configuration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.runner.Description;

public class AdmiralEventLogRule extends BaseReportRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final String REQUEST_STATUS_ENDPOINT = "/request-status?expand=true&$limit=1000";
    private final String EVENT_LOGS_ENDPOINT = "/resources/event-logs?expand=true&$limit=1000";
    private final String PROJECTS_ENDPOINT = "/projects?expand";
    private final String TARGET;
    private final List<String> PROJECT_NAMES;
    private final Supplier<String> AUTH_TOKEN_PROVIDER;
    private HttpClient client;

    private JsonParser parser;
    private Gson gson;

    public AdmiralEventLogRule(String target, List<String> projectNames,
            Supplier<String> authTokenProvider) {
        Objects.requireNonNull(target,
                "Parameter 'target' cannot be null");
        this.TARGET = target;
        this.PROJECT_NAMES = projectNames;
        this.AUTH_TOKEN_PROVIDER = authTokenProvider;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        if (Objects.isNull(PROJECT_NAMES)) {
            return;
        }
        List<Header> headers = null;
        String token = AUTH_TOKEN_PROVIDER.get();
        if (!Objects.isNull(token)) {
            headers = Arrays
                    .asList(new Header[] { new BasicHeader("x-xenon-auth-token", token) });
        }
        this.client = HttpUtils.createUnsecureHttpClient(null, headers);
        Map<String, String> nameToIdMap = getNameToIdMap();
        for (Entry<String, String> entry : nameToIdMap.entrySet()) {
            getAndWriteReports(entry.getKey(), entry.getValue(), description);
        }
    }

    private Map<String, String> getNameToIdMap() {
        String projectsResponse = executeGetRequest(TARGET + PROJECTS_ENDPOINT, null);
        JsonObject json = new Gson().fromJson(projectsResponse, JsonObject.class);
        JsonObject projects = json.get("documents").getAsJsonObject();
        Map<String, String> nameToIdMap = new HashMap<String, String>();
        for (String key : projects.keySet()) {
            String name = projects.get(key).getAsJsonObject().get("name").getAsString();
            if (PROJECT_NAMES.contains(name)) {
                nameToIdMap.put(name, key);
            }
        }
        return nameToIdMap;
    }

    private void getAndWriteReports(String projectName, String projectId,
            Description description) {
        parser = new JsonParser();
        gson = new GsonBuilder().setPrettyPrinting().create();
        String requestStatusTarget = TARGET + REQUEST_STATUS_ENDPOINT;
        String eventLogsTarget = TARGET + EVENT_LOGS_ENDPOINT;
        String requestStatusResponse = executeGetRequest(requestStatusTarget,
                projectId);
        JsonElement requestsJson = parser.parse(requestStatusResponse);
        String prettyRequests = gson.toJson(requestsJson);

        File requestsFile = new File(
                Configuration.reportsFolder + File.separator
                        + getPathFromClassAndMethodName(description.getClassName(),
                                description.getMethodName())
                        + description.getMethodName() + "-[" + projectName
                        + "]-request-status.json");
        try {
            FileUtils.writeStringToFile(requestsFile, prettyRequests, Charset.forName("UTF-8"));
        } catch (IOException e) {
            LOG.warning(String.format("Could not write requests status to: %s%nError: %s",
                    requestsFile.getAbsolutePath(), e.getMessage()));
        }
        LOG.info("Requests status: " + requestsFile.getAbsolutePath());

        String eventLogsResponse = executeGetRequest(eventLogsTarget, projectId);
        JsonElement logsJson = parser.parse(eventLogsResponse);
        String prettyLogs = gson.toJson(logsJson);

        File logsFile = new File(
                Configuration.reportsFolder + File.separator
                        + getPathFromClassAndMethodName(description.getClassName(),
                                description.getMethodName())
                        + description.getMethodName() + "-[" + projectName + "]-event-log.json");
        try {
            FileUtils.writeStringToFile(logsFile, prettyLogs, Charset.forName("UTF-8"));
        } catch (IOException e) {
            LOG.warning(String.format("Could not write event logs to: %s%nError: %s",
                    logsFile.getAbsolutePath(), e.getMessage()));
        }
        LOG.info("Event logs: " + logsFile.getAbsolutePath());
    }

    private String executeGetRequest(String target, String projectId) {
        HttpGet requestStatusGet = new HttpGet(target);
        if (Objects.nonNull(projectId)) {
            requestStatusGet.addHeader(new BasicHeader("x-project", projectId));
        }
        try {
            HttpResponse response = client.execute(requestStatusGet);
            String responseBody = EntityUtils.toString(response.getEntity());
            EntityUtils.consume(response.getEntity());
            return responseBody;
        } catch (IOException e) {
            LOG.warning("Could execute GET to: " + target + "\nError: " + e.getMessage());
            return null;
        }
    }

}
