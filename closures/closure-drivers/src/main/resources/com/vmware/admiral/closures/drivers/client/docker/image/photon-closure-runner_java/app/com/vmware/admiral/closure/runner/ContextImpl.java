package com.vmware.admiral.closure.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import com.google.gson.JsonObject;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import com.vmware.admiral.closure.runtime.Context;

public class ContextImpl implements Context {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PATCH = "PATCH";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String TOKEN = System.getenv("TOKEN");
    public static final String CLOSURE_URI = System.getenv("TASK_URI");

    public String closureUri;
    public String closureSemaphore;
    public JsonObject inputs;
    public JsonObject outputs = new JsonObject();

    public ContextImpl(String closureUri, String closureSemaphore, JsonObject inputs) {
        this.closureUri = closureUri;
        this.closureSemaphore = closureSemaphore;
        this.inputs = inputs;
    }

    @Override
    public String getInputs() {
        if (inputs == null) {
            return "";
        }
        return inputs.toString();
    }

    @Override
    public String getOutputs() {
        if (outputs == null) {
            return "";
        }
        return outputs.toString();
    }

    @Override
    public void execute(String link, String operation, String body, String handler)
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
            System.out.println("Unsupported operation on context object: " + op);
            break;
        }

        if (handler != null) {
            System.out.println(resp);
        }
    }

    // private methods

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
