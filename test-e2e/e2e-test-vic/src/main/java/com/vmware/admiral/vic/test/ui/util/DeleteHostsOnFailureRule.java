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

package com.vmware.admiral.vic.test.ui.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.vmware.admiral.test.util.HttpUtils;

public class DeleteHostsOnFailureRule extends TestWatcher {

    private static final String ADMIRAL_CLUSTERS_ENDPOINT = "/resources/clusters";

    private HttpClient client;

    private final String TARGET;
    private final String USERNAME;
    private final String PASSWORD;

    private final Logger LOG = Logger.getLogger(getClass().getName());

    public DeleteHostsOnFailureRule(String vicTarget, String adminUsername, String adminPassword) {
        this.TARGET = vicTarget;
        this.USERNAME = adminUsername;
        this.PASSWORD = adminPassword;
    }

    @Override
    protected void failed(Throwable e, Description description) {
        LOG.info("Deleting all projects hosts...");
        String authToken = VICAuthTokenGetter.getVICAuthToken(TARGET, USERNAME, PASSWORD);
        List<Header> headers = Arrays
                .asList(new Header[] { new BasicHeader("x-xenon-auth-token", authToken) });
        client = HttpUtils.createUnsecureHttpClient(null, headers);
        deleteAllProjectsHosts();
    }

    private void deleteAllProjectsHosts() {
        try {
            for (String link : getAllClustersSelfLinks()) {
                HttpDelete delete = new HttpDelete(TARGET + link);
                HttpResponse response = client.execute(delete);
                String responseBody = IOUtils.toString(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException(
                            "Could not delete cluster with link: " + link + ", response was:\n"
                                    + responseBody);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not delete clusters: " + e);
        }
    }

    private List<String> getAllClustersSelfLinks() throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(TARGET + ADMIRAL_CLUSTERS_ENDPOINT);
        HttpResponse response = client.execute(get);
        String responseBody = IOUtils.toString(response.getEntity().getContent());
        EntityUtils.consume(response.getEntity());
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException(
                    "Could not get clusters self links, response was:\n" + responseBody);
        }
        EntityUtils.consume(response.getEntity());
        DocumentLinks links = new Gson().fromJson(responseBody, DocumentLinks.class);
        return Arrays.asList(links.documentLinks);
    }

    private static class DocumentLinks {
        public String[] documentLinks;
    }

}
