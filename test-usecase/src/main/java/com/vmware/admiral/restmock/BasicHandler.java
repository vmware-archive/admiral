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

package com.vmware.admiral.restmock;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.StringUtil;

public class BasicHandler extends AbstractHandler {

    private static final Logger LOG =
            Logger.getLogger(BasicHandler.class.getName());
    private static final int DEFAULT_STATUS = 200;

    protected String path;
    protected Map<String, String> bodies;
    protected Map<String, Integer> statuses;

    public BasicHandler(String path) {
        this.path = path;
        bodies = Collections.synchronizedMap(new HashMap<String, String>());
        statuses = Collections.synchronizedMap(new HashMap<String, Integer>());
    }

    public String getBody(String method) {
        return bodies.get(method);
    }

    public void setBody(String method, String body) {
        bodies.put(method, body);
    }

    public int getStatus(String method) {
        return statuses.get(method);
    }

    public void setStatus(String method, int status) {
        statuses.put(method, status);
    }

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        if(target.equals(path)) {

            Map<String, String> headers = new HashMap<String, String>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }

            LOG.info(format(
                    "{0} {1} {2}",
                    request.getMethod(), request.getContextPath(), headers));

            if(handleSet(target, baseRequest, request)) {
                response.getWriter().println("Response updated");
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.setContentType("application/json; charset=UTF-8");
                response.getWriter().print(bodies.get(request.getMethod()));
                response.setStatus(statuses.get(request.getMethod()) != null ?
                        statuses.get(request.getMethod()) : DEFAULT_STATUS);
            }

            baseRequest.setHandled(true);
        }
    }

    protected boolean handleSet(String target, Request baseRequest, HttpServletRequest request) throws IOException {

        boolean handled = false;

        if("POST".equals(request.getMethod()) &&
                "application/json; charset=UTF-8".equals(request.getContentType()) &&
                StringUtil.isNotBlank(request.getPathInfo()) &&
                "/set".equals(request.getPathInfo())) {

            JsonElement el = new JsonParser().parse(request.getReader());
            JsonObject body = el.getAsJsonObject();
            String method = body.get("method").getAsString();
            String type = body.get("type").getAsString();
            String value = body.get("value").getAsString();

            if("body".equals(type)) {
                bodies.put(method, value);
                handled = true;
            } else if("status".equals(type)) {
                statuses.put(method, Integer.valueOf(value));
                handled = true;
            } else {
                throw new IllegalArgumentException(format("Wrong value for type ''{0}''", type));
            }
        }

        return handled;
    }
}
