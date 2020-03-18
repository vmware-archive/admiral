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

package com.vmware.admiral.client;

import static com.vmware.admiral.client.HttpClientUtils.execute;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

public class AdmiralClient {

    public enum ClientKind {
        XENON, VRA
    }

    public enum UserRole {
        MEMBER, ADMINISTRATOR, VIEWER
    }

    public enum PksEndpointOperation {
        VALIDATE, ACCEPT_CERTIFICATE, CREATE
    }

    private String uri, user, pass, tenant, token, basePath;
    private ClientKind kind;

    public AdmiralClient(String uri, String user, String pass, String tenant) {
        this(ClientKind.VRA, uri, user, pass, tenant);
    }

    public AdmiralClient(String uri, String user, String pass) {
        this(ClientKind.XENON, uri, user, pass, null);
    }

    public AdmiralClient(ClientKind kind, String uri, String user, String pass, String tenant) {
        this.kind = kind;
        this.uri = uri;
        this.user = user;
        this.pass = pass;
        this.tenant = tenant;

        switch (kind) {
        case VRA:
            basePath = "/container-service/api";
            break;

        case XENON:
        default:
            basePath = "";
            break;
        }
    }

    private void shouldLogin() throws Exception {
        if(isBlank(token)) {

            switch (kind) {
            case VRA:
                loginVRA();
                break;

            case XENON:
            default:
                loginXenon();
                break;
            }
        }
    }

    private void loginXenon() throws Exception {

        if(isBlank(token)) {

            String auth = format("{0}:{1}", user, pass);
            byte[] encodedAuth = Base64.getEncoder().encode(
                    auth.getBytes(StandardCharsets.ISO_8859_1));
            String authHeader = format("Basic {0}", new String(encodedAuth));

            Header xenonAuthHeader = execute(Request.Post(uri + "/core/authn/basic")
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .addHeader(HttpHeaders.AUTHORIZATION, authHeader)
                    .bodyString("{\"requestType\":\"LOGIN\"}", ContentType.APPLICATION_JSON))
                        .returnResponse()
                        .getFirstHeader("x-xenon-auth-token");

            if(xenonAuthHeader != null) {
                token = xenonAuthHeader.getValue();
            } else {
                throw new RuntimeException("Xenon login failed");
            }
        }
    }

    private void loginVRA() {
        if(isBlank(token)) {

            JsonObject payload = new JsonObject();
            payload.addProperty("username", user);
            payload.addProperty("password", pass);
            payload.addProperty("tenant", tenant);

            try {
                HttpResponse res = execute(Request.Post(uri + "/identity/api/tokens")
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .bodyString(new GsonBuilder().create().toJson(payload), ContentType.APPLICATION_JSON))
                            .returnResponse();

                int status = res.getStatusLine().getStatusCode();
                if(200 == status) {
                    JsonObject obj = new JsonParser().parse(
                            EntityUtils.toString(res.getEntity())).getAsJsonObject();
                    token = obj.get("id").getAsString();
                } else {
                    throw new AdmiralClientException(status,
                            format("vRA login failed with status ''{0}''", status));
                }
            } catch (Exception e) {
                throw new RuntimeException("vRA login failed");
            }
        }
    }

    public String createUsernameCredential(String name, String user, String pass) throws AdmiralClientException {

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "Password");
        payload.addProperty("userEmail", user);
        payload.addProperty("privateKey", pass);

        JsonObject customProperties = new JsonObject();
        customProperties.addProperty("__authCredentialsName", name);

        payload.add("customProperties", customProperties);

        HttpResponse res;
        try {
            res = post("/core/auth/credentials",
                    new GsonBuilder().create().toJson(payload));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        JsonObject obj = handleResponse(res).getAsJsonObject();
        return obj.get("documentSelfLink").getAsString();
    }

    public String createCertificate(String cert) throws AdmiralClientException {

        JsonObject payload = new JsonObject();
        payload.addProperty("certificate", cert);

        HttpResponse res;
        try {
            res = post("/config/trust-certs",
                    new GsonBuilder().create().toJson(payload),
                    new BasicHeader("pragma", "xn-force-index-update"));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        JsonObject obj = handleResponse(res).getAsJsonObject();
        return obj.get("documentSelfLink").getAsString();
    }

    /**
     * Validates or creates a PKS endpoint, based on the <code>op</code> flag.
     *
     * @param name Name of the endpoint
     * @param uaaEndpoint UAA endoint address with schema and port
     * @param pksEndpoint PKS endoint address with schema and port
     * @param credsLink Link to a previously created credential object
     * @param description Description of the endpoint
     * @param op Endpoint operation
     * @param acceptForHost The host for which the certificate should be accepted (UUA or PKS API), or <code>null</code>
     * if the operation is VALIDATE or CREATE
     * @return If VALIDATE, the certificate content, if ACCEPT_CERTIFICATE, an empty string, if CREATE, the link of the created endpoint.
     * @throws AdmiralClientException
     */
    public String validateOrCreatePksEndpoint(String name, String uaaEndpoint, String pksEndpoint,
            String credsLink, String description, PksEndpointOperation op, String acceptForHost) throws AdmiralClientException {

        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("name", name);
        endpoint.addProperty("uaaEndpoint", uaaEndpoint);
        endpoint.addProperty("apiEndpoint", pksEndpoint);
        endpoint.addProperty("authCredentialsLink", credsLink);
        endpoint.addProperty("desc", description);

        JsonObject payload = new JsonObject();
        payload.add("endpoint", endpoint);

        if(op == PksEndpointOperation.ACCEPT_CERTIFICATE) {
            payload.addProperty("acceptCertificate", true);
            payload.addProperty("acceptCertificateForHost", acceptForHost);
        }

        HttpResponse res;
        try {
            res = put("/resources/pks/create-endpoint"
                    + (op == PksEndpointOperation.VALIDATE || op == PksEndpointOperation.ACCEPT_CERTIFICATE ?
                            "?validate=true" : StringUtils.EMPTY),
                    new GsonBuilder().create().toJson(payload));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        String result = StringUtils.EMPTY;
        if(op == PksEndpointOperation.VALIDATE) {
            JsonElement el = handleResponse(res);
            if(el != null) {
                result = handleResponse(res).getAsJsonObject()
                        .get("certificate").getAsString();
            }
        } else if(op == PksEndpointOperation.ACCEPT_CERTIFICATE) {
            handleResponse(res);
        } else {
            Header location = res.getFirstHeader("location");
            if(location != null) {
                result = location.getValue();
            } else {
                throw new AdmiralClientException("Did not get PKS endpoint self link");
            }
        }
        return result;
    }

    public List<String> getPksEndpoints() throws Exception {

        HttpResponse res = get("/resources/pks/endpoints");
        JsonObject obj = handleResponse(res).getAsJsonObject();

        List<String> endpoints = new ArrayList<>();
        for (JsonElement link : obj.get("documentLinks").getAsJsonArray()) {
            endpoints.add(link.getAsString());
        }

        return endpoints;
    }

    public String getPksEndpoint(String endpoint) throws Exception {
        HttpResponse res = get(format("/resources/pks/endpoints/{0}", endpoint));
        JsonObject obj = handleResponse(res).getAsJsonObject();
        return obj.toString();
    }

    public List<JsonObject> getPksClusters(String endpointLink) throws Exception {

        HttpResponse res = get(format(
                "/resources/pks/clusters?endpointLink={0}", endpointLink));
        JsonArray clustersJson = handleResponse(res).getAsJsonArray();

        List<JsonObject> clusters = new ArrayList<>();
        for (JsonElement cluster : clustersJson) {
            clusters.add(cluster.getAsJsonObject());
        }

        return clusters;
    }

    public String addPksClusterToProject(String endpointLink,
            JsonObject clusterJson, String projectLink) throws AdmiralClientException {

        JsonObject payload = new JsonObject();
        payload.addProperty("endpointLink", endpointLink);
        payload.add("cluster", clusterJson);

        HttpResponse res;
        try {
            res = post("/resources/pks/clusters-config",
                    new GsonBuilder().create().toJson(payload),
                    new BasicHeader("x-project", projectLink));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        return handleResponse(res).getAsJsonObject().get("documentSelfLink").getAsString();
    }

    public void addUserToProject(String userId,
            UserRole role, String projectLink) throws AdmiralClientException {

        JsonArray member = new JsonArray();
        member.add(userId);

        JsonObject members = new JsonObject();
        members.add(UserRole.MEMBER == role ? "add" : "remove", member);

        JsonObject administrators = new JsonObject();
        administrators.add(UserRole.ADMINISTRATOR == role ? "add" : "remove", member);

        JsonObject viewers = new JsonObject();
        viewers.add(UserRole.VIEWER == role ? "add" : "remove", member);

        JsonObject payload = new JsonObject();
        payload.add("members", members);
        payload.add("administrators", members);
        payload.add("viewers", members);

        HttpResponse res;
        try {
            res = patch(projectLink, new GsonBuilder().create().toJson(payload));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        handleResponse(res);
    }

    public String createProject(String name, String desc, boolean isPublic) throws AdmiralClientException {

        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("description", desc);
        payload.addProperty("isPublic", String.valueOf(isPublic));

        HttpResponse res;
        try {
            res = post("/projects", new GsonBuilder().create().toJson(payload));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        JsonObject obj = handleResponse(res).getAsJsonObject();
        return obj.get("documentSelfLink").getAsString();
    }

    public JsonArray getResources(String path) throws AdmiralClientException {
        return getResources(path, null);
    }


    public JsonArray getResources(String path, String projectLink) throws AdmiralClientException {

        List<Header> headers = new ArrayList<>();
        if(projectLink != null) {
            headers.add(new BasicHeader("x-project", projectLink));
        }

        HttpResponse res;
        try {
            res = get(path, headers.toArray(new Header[]{}));
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        return handleResponse(res).getAsJsonObject().get("documentLinks").getAsJsonArray();
    }

    public JsonObject getResource(String path) throws AdmiralClientException {

        HttpResponse res;
        try {
            res = get(path);
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }

        return handleResponse(res).getAsJsonObject();
    }

    public boolean deleteResource(String path) throws AdmiralClientException {
        boolean deleted = false;
        try {
            HttpResponse res = delete(path);
            if(200 == res.getStatusLine().getStatusCode()) {
                deleted = true;
            }
        } catch (Exception e) {
            throw new AdmiralClientException(e);
        }
        return deleted;
    }

    private HttpResponse delete(String path) throws Exception {
        shouldLogin();
        Request req = Request.Delete(uri + basePath + path)
                .addHeader("Content-Type", "application/json; charset=UTF-8");

        return execute(addAuthHeader(req)).returnResponse();
    }

    private HttpResponse post(String path, String payload, Header... headers) throws Exception {
        shouldLogin();
        Request req = Request.Post(uri + basePath + path)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .bodyString(payload, ContentType.APPLICATION_JSON);

        if(headers != null) {
            for (Header header : headers) {
                req.addHeader(header);
            }
        }

        return execute(addAuthHeader(req)).returnResponse();
    }

    private HttpResponse patch(String path, String payload, Header... headers) throws Exception {
        shouldLogin();
        Request req = Request.Patch(uri + basePath + path)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .bodyString(payload, ContentType.APPLICATION_JSON);

        if(headers != null) {
            for (Header header : headers) {
                req.addHeader(header);
            }
        }

        return execute(addAuthHeader(req)).returnResponse();
    }

    private HttpResponse put(String path, String payload, Header... headers) throws Exception {
        shouldLogin();
        Request req = Request.Put(uri + basePath + path)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .bodyString(payload, ContentType.APPLICATION_JSON);

        if(headers != null) {
            for (Header header : headers) {
                req.addHeader(header);
            }
        }

        return execute(addAuthHeader(req)).returnResponse();
    }

    private HttpResponse get(String path, Header... headers) throws Exception {
        shouldLogin();
        Request req = Request.Get(uri + basePath + path)
                .addHeader("Content-Type", "application/json; charset=UTF-8");

        if(headers != null) {
            for (Header header : headers) {
                req.addHeader(header);
            }
        }

        return execute(addAuthHeader(req)).returnResponse();
    }

    private Request addAuthHeader(Request req) {

        switch (kind) {
        case VRA:
            req.addHeader("Authorization: ", format("{0} {1}", "Bearer ", token));
            break;

        case XENON:
        default:
            req.addHeader("x-xenon-auth-token", token);
            break;
        }

        return req;
    }

    private JsonElement handleResponse(HttpResponse res) throws AdmiralClientException {
        int status = res.getStatusLine().getStatusCode();
        if(200 == status || 204 == status) {
            try {
                JsonElement el = null;
                if(res.getEntity() != null) {
                    el = new JsonParser().parse(
                        EntityUtils.toString(res.getEntity()));
                }
                return el;
            } catch (Exception e) {
                throw new AdmiralClientException(e);
            }
        } else {
            throw new AdmiralClientException(status,
                    format("Request failed with status ''{0}''", status));
        }
    }
}
