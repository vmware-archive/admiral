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

package com.vmware.admiral.closure.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import com.vmware.admiral.closure.runtime.Context;

/**
 * Implementation of Context interface
 *
 * @see Context
 */
public class ContextImpl implements Context {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PATCH = "PATCH";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String TOKEN = System.getenv("TOKEN");
    public static final String CLOSURE_URI = System.getenv("TASK_URI");

    private Gson gson;

    private String closureUri;
    private String closureSemaphore;
    private JsonObject inputs;
    private JsonObject outputs = new JsonObject();

    public ContextImpl(String closureUri, String closureSemaphore, JsonObject inputs) {
        this.closureUri = closureUri;
        this.closureSemaphore = closureSemaphore;
        this.inputs = inputs;

        gson = new GsonBuilder().setLenient().create();
    }

    @Override
    public Map<String, Object> getInputs() {
        if (inputs == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> inMap = new HashMap<>();
        Set<Map.Entry<String, JsonElement>> entries = inputs.entrySet();
        entries.forEach((k) -> inMap.put(k.getKey(), k.getValue()));

        return inMap;
    }

    @Override
    public void setOutput(String key, Object value) {
        JsonElement jEl = convertToJsonElement(value);
        outputs.add(key, jEl);
    }

    @Override
    public String getOutputsAsString() {
        if (outputs == null) {
            return "";
        }
        return outputs.toString();
    }

    @Override
    public void execute(String link, String operation, String body, Consumer<String> handler)
            throws Exception {
        String op = operation.toUpperCase();
        String targetUri = buildUri(link);
        HttpClient client = HttpClientBuilder.create().build();
        String resp = null;
        switch (op) {
        case GET:
            HttpGet get = new HttpGet(targetUri);
            resp = executeRequest(get, client);
            break;
        case POST:
            HttpPost post = new HttpPost(targetUri);
            resp = executeRequestWithBody(post, client, body);
            break;
        case PATCH:
            HttpPatch patch = new HttpPatch(targetUri);
            resp = executeRequestWithBody(patch, client, body);
            break;
        case PUT:
            HttpPut put = new HttpPut(targetUri);
            resp = executeRequestWithBody(put, client, body);
            break;
        case DELETE:
            HttpDelete delete = new HttpDelete(targetUri);
            resp = executeRequest(delete, client);
            break;
        default:
            String errMsg = "Unsupported operation on context object: " + op;
            System.err.println(errMsg);
            throw new Exception(errMsg);
        }

        if (handler != null) {
            handler.accept(resp);
        }
    }

    // private methods

    private JsonElement convertToJsonElement(Object value) {
        if (value instanceof JsonElement) {
            return (JsonElement) value;
        } else if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        } else if (value instanceof Character) {
            return new JsonPrimitive((Character) value);
        } else if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        } else {
            return gson.fromJson(value.toString(), JsonElement.class);
        }
    }

    private String executeRequest(HttpRequest request, HttpClient client) {
        setHeaders(request);
        String resp = null;
        try {
            HttpResponse response = client.execute((HttpUriRequest) request);
            resp = readResponse(response.getEntity().getContent());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resp;
    }

    private String executeRequestWithBody(HttpEntityEnclosingRequestBase request, HttpClient client,
            String body) throws Exception {
        setHeaders(request);
        try {
            request.setEntity(new StringEntity(body));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw e;
        }
        String resp = null;
        try {
            HttpResponse response = client.execute(request);
            resp = readResponse(response.getEntity().getContent());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
        return resp;
    }

    private String readResponse(InputStream response) throws IOException {
        BufferedReader reader = null;
        StringBuffer result = new StringBuffer();
        try {
            reader = new BufferedReader(new InputStreamReader(response));
            String line = "";
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return result.toString();
    }

    private String buildUri(String link) {
        String pattern = "/resources/closures/";
        String uriHead = CLOSURE_URI.split(pattern)[0];
        return uriHead + link;
    }

    private void setHeaders(HttpRequest request) {
        request.setHeader("Content-type", "application/json");
        request.setHeader("Accept", "application/json");
        request.setHeader("x-xenon-auth-token", TOKEN);
    }

}
