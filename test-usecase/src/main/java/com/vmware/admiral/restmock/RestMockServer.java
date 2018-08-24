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

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class RestMockServer {

    private static final Logger LOG =
               Logger.getLogger(RestMockServer.class.getName());

    private int port;
    private Server server;
    private Map<String, BasicHandler> handlersMap;
    private HandlerCollection handlers;

    public RestMockServer() {
        port = MockUtils.getAvailablePort();
        server = setupSecureConnector(port);
        handlers = new HandlerCollection(true);
        handlersMap = Collections.synchronizedMap(
                    new HashMap<String, BasicHandler>());
    }

    public void createMock(String method, String path, String payload, int status) {

        BasicHandler handler = handlersMap.get(path);

        if(handler == null) {
            handler = new BasicHandler(path);
            handlersMap.put(path, handler);
            handlers.addHandler(handler);
        }

        handler.setBody(method, payload);
        handler.setStatus(method, status);
    }

    public void removeMock(String path) {

        BasicHandler handler = handlersMap.remove(path);
        if(handler != null) {
            handlers.removeHandler(handler);
        }
    }

    public void removeMocks(String ...paths) {
        for (String path : paths) {
            removeMock(path);
        }
    }

    public void start() throws Exception {

        LOG.info("Starting mock server on port " +  port);

        server.setHandler(handlers);
        server.start();
    }

    public void stop() throws Exception {

        LOG.info("Stopping mock server");

        server.stop();
    }

    public int getPort() {
        return port;
    }

    private Server setupSecureConnector(int port) {

        KeyStore keystore;
        try(InputStream is = RestMockServer.class.getResourceAsStream(
                "/environment/jetty.pkcs12")) {
            keystore = KeyStore.getInstance("PKCS12");
            keystore.load(is, "jetty".toCharArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SslContextFactory sslCtxFactory = new SslContextFactory();
        sslCtxFactory.setKeyStore(keystore);
        sslCtxFactory.setKeyStorePassword("jetty");

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecurePort(port);

        Server server = new Server();
        ServerConnector https = new ServerConnector(server,
                new SslConnectionFactory(sslCtxFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpConfig));
        https.setPort(port);
        server.addConnector(https);
        return server;
    }
}
