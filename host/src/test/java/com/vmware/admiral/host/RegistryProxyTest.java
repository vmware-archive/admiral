/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.container.TemplateSearchService;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

public class RegistryProxyTest {

    private final String tmpDirPath = "tmp_registry_proxy_test";
    private final String propertiesWithPoxyFilePath = "customconfig-proxy.properties";
    private final String templateQuery =
            "templates?q=asddsa&imagesOnly=true&documentType=true&%24limit=10000";

    File tmpDir;
    File configFile;

    ProxyThread proxyThread;
    VerificationHost host;
    ServerSocket proxySocket;

    @After
    public void cleanUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
        if (host != null) {
            host.stop();
        }
        if (proxyThread != null) {
            proxyThread.stopProxy();
        }
        if (tmpDir != null) {
            FileUtils.deleteDirectory(tmpDir);
        }

        // reset the ConfigurationService.CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES
        setCustomConfiguration(null);

    }

    protected void createHostBehindCluster(boolean withExceptionList) throws Throwable {
        Assert.assertNotNull(proxySocket);
        tmpDir = new File(tmpDirPath);
        tmpDir.mkdirs();

        configFile = new File(tmpDir.getPath() + File.separator + propertiesWithPoxyFilePath);

        try {
            Properties props = new Properties();
            props.setProperty("registry.proxy", "http://localhost:" + proxySocket.getLocalPort());
            if (withExceptionList) {
                props.setProperty("registry.no.proxy.list", "registry.hub.docker.com");
            }
            props.setProperty("register.user.interval.delay", "120");
            FileWriter writer = new FileWriter(configFile);
            props.store(writer, "host settings");
            writer.close();
        } catch (FileNotFoundException ex) {
            System.out.println("Configuration file not found.");
        } catch (IOException ex) {
            System.out.println("IOException when creatin the configuration file.");
        }

        //System.setProperty("configuration.properties", configFile.getAbsolutePath());
        //The ConfigurationService.CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES is final
        //static.Therefore, when we run multiple test it can not be changed properly so
        //of using system property we set the value by reflexion
        setCustomConfiguration(configFile.getAbsolutePath());

        host = VerificationHost.initialize(new VerificationHost(),
                VerificationHost.buildDefaultServiceHostArguments(getFreePort()));
        host.start();
        HostInitCommonServiceConfig.startServices(host);

        DeploymentProfileConfig.getInstance().setTest(true);
        HostInitComputeServicesConfig.startServices(host, false);
        HostInitImageServicesConfig.startServices(host);
        HostInitRegistryAdapterServiceConfig.startServices(host);

        TestContext ctx = BaseTestCase.testCreate(2);
        host.registerForServiceAvailability(ctx.getCompletion(),
                TemplateSearchService.SELF_LINK,
                ContainerImageService.SELF_LINK);
        ctx.await();

    }

    protected void startProxy() throws InterruptedException {
        Assert.assertNotNull(proxySocket);
        Assert.assertNotNull(host);
        proxyThread = new ProxyThread(proxySocket, host);
        proxyThread.start();

        Thread.sleep(1000);

    }

    @Test
    public void testHostBehindProxyWithNoExceptionList() throws Throwable {

        proxySocket = new ServerSocket(0);
        createHostBehindCluster(false);
        startProxy();

        Assert.assertEquals(0, proxyThread.messagesCounter);

        queryTemplateImages();

        Assert.assertEquals(1, proxyThread.messagesCounter);

    }

    @Test
    public void testHostBehindProxyWithExceptionList() throws Throwable {

        proxySocket = new ServerSocket(0);
        createHostBehindCluster(true);
        startProxy();

        Assert.assertEquals(0, proxyThread.messagesCounter);

        queryTemplateImages();

        Assert.assertEquals(0, proxyThread.messagesCounter);

    }

    private void queryTemplateImages() {
        TestContext ctx = BaseTestCase.testCreate(1);

        host.sendRequest(Operation.createGet(UriUtils.buildUri(host,
                templateQuery))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);

                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();
    }

    private void setCustomConfiguration(String value) throws NoSuchFieldException,
            IllegalAccessException {
        Field field = ConfigurationService.class
                .getDeclaredField("CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES");
        field.setAccessible(true);
        Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, value);
    }

    public int getFreePort() throws IOException {
        int result = 0;
        ServerSocket tempServer = new ServerSocket(0);
        tempServer.setReuseAddress(true);
        result = tempServer.getLocalPort();
        tempServer.close();
        return result;
    }

    class ProxyThread extends Thread {
        private ServerSocket serverSocket;
        private ServiceHost managementHost;
        private static final int BUFFER_SIZE = 32768;
        private static final String OUTPUT_HEADERS_OK = "HTTP/1.1 200 OK";
        private static final String OUTPUT_END_OF_HEADERS = "\r\n\r\n";

        private int messagesCounter;

        private boolean listening;

        public ProxyThread() {
            this(null, null);
        }

        public ProxyThread(ServerSocket serverSocket) {
            this(serverSocket, null);
        }

        public ProxyThread(ServerSocket serverSocket, ServiceHost managementHost) {
            super("ProxyThread");

            if (serverSocket == null) {
                try {
                    this.serverSocket = new ServerSocket(0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                this.serverSocket = serverSocket;
            }

            this.managementHost = managementHost;
            messagesCounter = 0;
            this.listening = true;

        }

        public void stopProxy() {
            this.listening = false;
        }

        public int getProxyPort() {
            return serverSocket.getLocalPort();
        }

        public int getMessagesCounter() {
            return messagesCounter;
        }

        @Override
        public void run() {
            /**
             * Steps:
             * 1. get input from user
             * 2. send request to server
             * 3. get response from server
             * 4. send response to user
             */

            while (listening) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e1) {
                    log("Proxy socket accept exception : " + e1);

                }
                try {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    String inputLine;
                    int cnt = 0;
                    String urlToCall = "";

                    /**
                     * 1. get input from user
                     */
                    while ((inputLine = in.readLine()) != null) {
                        try {
                            StringTokenizer tok = new StringTokenizer(inputLine);
                            tok.nextToken();
                        } catch (Exception e) {
                            break;
                        }
                        //parse the first line of the request to find the url
                        if (cnt == 0) {
                            String[] tokens = inputLine.split(" ");
                            urlToCall = tokens[1];
                            //can redirect this to output log
                            log("Request for : " + urlToCall);
                            messagesCounter++;
                        }

                        cnt++;
                    }

                    /**
                     * 2. send request to server
                     * 3. get response from server
                     */

                    BufferedWriter bw = null;
                    try {
                        //2. send request to server
                        URL url = new URL(urlToCall);
                        URLConnection conn = url.openConnection();
                        conn.setDoInput(true);
                        conn.setDoOutput(false);

                        // 3. get response from server
                        InputStream is = null;
                        HttpURLConnection huc = (HttpURLConnection) conn;
                        if (conn.getContentLength() > 0) {
                            try {
                                is = conn.getInputStream();
                            } catch (IOException ioe) {
                                System.out.println(
                                        "********* IO EXCEPTION **********: " + ioe);
                            }
                        }

                        /**
                         * 4. send response to user
                         */
                        byte[] by = new byte[BUFFER_SIZE];
                        int index = is.read(by, 0, BUFFER_SIZE);
                        String responceBody = "";
                        while (index != -1) {
                            responceBody += new String(by, 0, index);
                            index = is.read(by, 0, BUFFER_SIZE);
                        }

                        StringBuilder responseBuilder = new StringBuilder(OUTPUT_HEADERS_OK);

                        Map<String, List<String>> map = conn.getHeaderFields();

                        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                            if (entry.getKey() != null) {
                                responseBuilder.append("\r\n");
                                responseBuilder.append(entry.getKey());
                                responseBuilder.append(": ");
                                responseBuilder.append(entry.getValue().get(0));
                            }
                        }

                        bw = new BufferedWriter(
                                new OutputStreamWriter(
                                        new BufferedOutputStream(socket.getOutputStream()), "UTF-8")
                        );
                        responseBuilder.append(OUTPUT_END_OF_HEADERS);
                        responseBuilder.append(responceBody);
                        bw.write(responseBuilder.toString());
                        bw.flush();

                    } catch (Exception e) {
                        log("Proxy listening cycle exeption : " + e);
                    } finally {

                        //close out all resources
                        if (bw != null) {
                            bw.close();
                        }
                        if (in != null) {
                            in.close();
                        }
                        if (socket != null) {
                            socket.close();
                        }
                    }

                } catch (IOException e) {
                    log("Proxy general exception: " + e.getMessage());
                }
            }
        }

        private void log(String message) {
            if (managementHost == null) {
                System.out.println(message);
            } else {
                managementHost.log(Level.WARNING, message);
            }
        }
    }

}
