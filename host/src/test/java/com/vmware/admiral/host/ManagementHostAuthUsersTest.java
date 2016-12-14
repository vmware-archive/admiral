/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static com.vmware.xenon.services.common.authn.BasicAuthenticationUtils.constructBasicAuth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class ManagementHostAuthUsersTest extends ManagementHostBaseTest {

    private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new AllowAllHostnameVerifier();
    private static final SSLSocketFactory UNSECURE_SSL_SOCKET_FACTORY = getUnsecuredSSLSocketFactory();

    private static final String LOCAL_USERS_FILE = "/local-users.json";

    private static final String USERNAME = "administrator@admiral.com";
    private static final String PASSWORD = "Password1!";

    public static final String PROTECTED_ENDPOINT = RegistryService.FACTORY_LINK
            + "/default-registry";

    private static final TemporaryFolder sandbox = new TemporaryFolder();
    private static ManagementHost host;

    @BeforeClass
    public static void setUp() throws Throwable {
        sandbox.create();
        String configFile = ManagementHostTest.class.getResource(LOCAL_USERS_FILE).toURI()
                .getPath();
        host = ManagementHostBaseTest.createManagementHost(new String[] {
                CommandLineArgumentParser.ARGUMENT_PREFIX
                        + HostInitAdapterServiceConfig.FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE
                        + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                        + Boolean.TRUE.toString(),
                CommandLineArgumentParser.ARGUMENT_PREFIX
                        + AuthBootstrapService.LOCAL_USERS_FILE
                        + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                        + configFile,
                CommandLineArgumentParser.ARGUMENT_PREFIX
                        + "sandbox"
                        + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                        // generate a random sandbox
                        + sandbox.getRoot().toPath(),
                CommandLineArgumentParser.ARGUMENT_PREFIX
                        + "port"
                        + CommandLineArgumentParser.ARGUMENT_ASSIGNMENT
                        // ask runtime to pick a random port
                        + "0" });

        AuthBootstrapService.waitForInitConfig(host, host.localUsers);
    }

    @AfterClass
    public static void tearDown() {
        if (host == null) {
            return;
        }
        host.stop();
        sandbox.delete();
    }

    @Test
    public void testInvalidAuth() throws Throwable {

        String token;

        token = login(host, USERNAME, "bad");
        assertNull(token);

        token = login(host, "bad", PASSWORD);
        assertNull(token);
    }

    @Test
    public void testRestrictedOperationWithoutAuth() throws Throwable {

        int statusCode = doRestrictedOperation(host, null);

        assertEquals("Operation should be forbidden!", HttpURLConnection.HTTP_FORBIDDEN,
                statusCode);
    }

    @Test
    public void testRestrictedOperationWithAuth() throws Throwable {

        String token = login(host, USERNAME, PASSWORD);
        assertNotNull(token);

        /*
         * This test sets the token as a header of the request, but it's possible also (and
         * recommended) to provide the token as a cookie if the client supports it
         * (e.g. any browser, Postman, HttpClient, etc.).
         */
        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, token);

        int statusCode = doRestrictedOperation(host, headers);

        assertEquals("Operation should be OK!", HttpURLConnection.HTTP_OK, statusCode);
    }

    /*
     * Invokes a GET operation on a protected endpoint and returns the status code returned by the
     * server.
     */
    public static int doRestrictedOperation(ManagementHost host, Map<String, String> headers)
            throws IOException {

        URI uri = UriUtils.buildUri(host, PROTECTED_ENDPOINT);

        SimpleEntry<Integer, String> result = doGet(uri, headers);

        return result.getKey();
    }

    public static SimpleEntry<Integer, String> doGet(URI uri, Map<String, String> headers)
            throws IOException {

        HttpURLConnection conn = getConnection(uri);
        conn.setRequestMethod("GET");

        if (headers != null) {
            for (Entry<String, String> header : headers.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        System.out.println(">>>> Sending 'GET' request to URL : " + uri);

        int responseCode = conn.getResponseCode();

        String responseBody;
        try {
            responseBody = readStream(conn.getInputStream());
        } catch (IOException e) {
            responseBody = readStream(conn.getErrorStream());
        }

        System.out.println("Response Code : " + responseCode);
        System.out.println("Response Body : " + responseBody);

        return new SimpleEntry<>(responseCode, responseBody);
    }

    /*
     * Returns the auth token or null if the credentials are invalid.
     */
    public static String login(ServiceHost host, String username, String password)
            throws IOException {

        URI uri = UriUtils.buildUri(host, BasicAuthenticationService.SELF_LINK);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", constructBasicAuth(username, password));
        headers.put("Content-Type", "application/json");

        String body = "{\"requestType\":\"LOGIN\"}";

        SimpleEntry<Integer, String> result = doPost(uri, headers, body);

        return result.getValue();
    }

    public static SimpleEntry<Integer, String> doPost(URI uri, Map<String, String> headers,
            String body) throws IOException {

        HttpURLConnection conn = getConnection(uri);
        conn.setRequestMethod("POST");

        if (headers != null) {
            for (Entry<String, String> header : headers.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
        }

        System.out.println(">>>> Sending 'POST' request to URL : " + uri);

        conn.setDoOutput(true);

        writeStream(conn.getOutputStream(), body);

        int responseCode = conn.getResponseCode();

        String responseBody = conn.getHeaderField(Operation.REQUEST_AUTH_TOKEN_HEADER);

        System.out.println("Response Code : " + responseCode);
        System.out.println("Response Body : " + responseBody);

        return new SimpleEntry<>(responseCode, responseBody);
    }

    private static String readStream(InputStream stream) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(stream))) {
            return buffer.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private static void writeStream(OutputStream stream, String body) throws IOException {
        try (BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(stream))) {
            buffer.write(body);
        }
    }

    private static HttpURLConnection getConnection(URI uri) throws IOException {
        URL url = uri.toURL();
        if (url.getProtocol().equalsIgnoreCase("https")) {
            HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
            conn.setHostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
            conn.setSSLSocketFactory(UNSECURE_SSL_SOCKET_FACTORY);
            return conn;
        } else {
            return (HttpURLConnection) url.openConnection();
        }
    }

    private static class AllowAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    protected static SSLSocketFactory getUnsecuredSSLSocketFactory() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    null,
                    new TrustManager[] { UnsecuredX509TrustManager.getInstance() },
                    null);
            return context.getSocketFactory();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private static class UnsecuredX509TrustManager implements X509TrustManager {

        private static UnsecuredX509TrustManager instance;

        private UnsecuredX509TrustManager() {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public static UnsecuredX509TrustManager getInstance() {
            if (instance == null) {
                instance = new UnsecuredX509TrustManager();
            }
            return instance;
        }
    }

}
