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

package com.vmware.admiral;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Simple HTTPS client for executing HTTPS requests
 */
public class SimpleHttpsClient {
    private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new AllowAllHostnameVerifier();

    public enum HttpMethod {
        GET, POST, PUT, PATCH, DELETE
    }

    public static class HttpResponse {
        public int statusCode;
        public String responseBody;
        public Map<String, List<String>> headers;
    }

    /**
     * Execute an HTTP request
     */
    public static HttpResponse execute(HttpMethod method, String targetUrl, String body)
            throws IOException,
            KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        return execute(method, targetUrl, body, null);
    }

    /**
     * Execute an HTTP request without body
     */
    public static HttpResponse execute(HttpMethod method, String targetUrl) throws IOException,
            KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        return execute(method, targetUrl, null, null);
    }

    public static HttpResponse execute(HttpMethod method, String targetUrl, String body,
                                       SSLSocketFactory sslSocketFactory) throws IOException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException, KeyManagementException {
        return execute(method, targetUrl, body, Collections.emptyMap(), sslSocketFactory);
    }

    /**
     * Execute an HTTPS request
     *
     * @param method
     * @param targetUrl
     * @param body
     * @param headers
     * @param sslSocketFactory
     * @throws IOException
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws KeyManagementException
     */
    public static HttpResponse execute(HttpMethod method, String targetUrl, String body,
                                       Map<String, String> headers,
                                       SSLSocketFactory sslSocketFactory) throws IOException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException, KeyManagementException {
        URL url = new URL(targetUrl);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setRequestMethodUsingWorkaroundForJREBug(conn, method.name());
        conn.addRequestProperty("Content-type", Operation.MEDIA_TYPE_APPLICATION_JSON);
        conn.addRequestProperty("Accept", Operation.MEDIA_TYPE_APPLICATION_JSON);

        Operation op = new Operation()
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        conn.addRequestProperty(Operation.PRAGMA_HEADER,
                op.getRequestHeader(Operation.PRAGMA_HEADER));

        for (Entry<String, String> entry : headers.entrySet()) {
            conn.addRequestProperty(entry.getKey(), entry.getValue());
        }

        if (sslSocketFactory != null && UriUtils.HTTPS_SCHEME.equals(url.getProtocol())) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
            httpsConn.setHostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
            httpsConn.setSSLSocketFactory(sslSocketFactory);
        } else if (UriUtils.HTTPS_SCHEME.equals(url.getProtocol())) {
            throw new IllegalArgumentException(
                    "Https protocol not supported without sslSocketFactory");
        }

        if (body != null) {
            conn.setDoOutput(true);
            DataOutputStream dataOut = new DataOutputStream(conn.getOutputStream());
            dataOut.writeBytes(body);
            dataOut.flush();
            dataOut.close();
        }

        BufferedReader in = null;
        try {
            try {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), Utils.CHARSET));
            } catch (Throwable e) {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    in = new BufferedReader(new InputStreamReader(errorStream, Utils.CHARSET));
                }
            }
            StringBuilder stringResponseBuilder = new StringBuilder();

            HttpResponse httpResponse = new HttpResponse();
            httpResponse.statusCode = conn.getResponseCode();

            if (in == null) {
                return validateResponse(httpResponse);
            }
            do {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                stringResponseBuilder.append(line);
            } while (true);

            httpResponse.responseBody = stringResponseBuilder.toString();
            httpResponse.headers = conn.getHeaderFields();
            return validateResponse(httpResponse);
        } finally {
            if (in != null) {
                in.close();
            }
        }

    }

    protected static HttpResponse validateResponse(HttpResponse httpResponse) {
        if (httpResponse.statusCode < 200) {
            logWarning("Http status code not expected: %d", httpResponse.statusCode);
        } else if (httpResponse.statusCode >= 300 && httpResponse.statusCode < 400) {
            logWarning("Http redirect status code is not expected: %d", httpResponse.statusCode);
        } else if (httpResponse.statusCode == 404) {
            httpResponse.responseBody = null;
            return httpResponse;
        } else if (httpResponse.statusCode >= 400 && httpResponse.statusCode < 500) {
            logError("Http validation error. Status code: %s", httpResponse.statusCode);
            throw new IllegalArgumentException(httpResponse.responseBody);
        } else if (httpResponse.statusCode >= 500) {
            logError("Http server error. Status code: %s", httpResponse.statusCode);
            throw new IllegalArgumentException(httpResponse.responseBody);
        }
        return httpResponse;
    }

    protected static void logError(String fmt, Object... args) {
        Utils.log(SimpleHttpsClient.class, "HttpRequest", Level.SEVERE, fmt, args);
    }

    protected static void logWarning(String fmt, Object... args) {
        Utils.log(SimpleHttpsClient.class, "HttpRequest", Level.WARNING, fmt, args);
    }

    /**
     * HostnameVerifier that accepts any hostname
     */
    private static class AllowAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    // Copied from https://java.net/jira/browse/JERSEY-639 . This allows us to use the new HTTP
    // methods like PATCH
    private static void setRequestMethodUsingWorkaroundForJREBug(
            final HttpURLConnection httpURLConnection, final String method) {
        try {
            httpURLConnection.setRequestMethod(method);
            // Check whether we are running on a buggy JRE
        } catch (final ProtocolException pe) {
            Class<?> connectionClass = httpURLConnection
                    .getClass();
            Field delegateField = null;
            try {
                delegateField = connectionClass.getDeclaredField("delegate");
                delegateField.setAccessible(true);
                HttpURLConnection delegateConnection = (HttpURLConnection) delegateField
                        .get(httpURLConnection);
                setRequestMethodUsingWorkaroundForJREBug(delegateConnection, method);
            } catch (NoSuchFieldException e) {
                // Ignore for now, keep going
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            try {
                Field methodField;
                while (connectionClass != null) {
                    try {
                        methodField = connectionClass
                                .getDeclaredField("method");
                    } catch (NoSuchFieldException e) {
                        connectionClass = connectionClass.getSuperclass();
                        continue;
                    }
                    methodField.setAccessible(true);
                    methodField.set(httpURLConnection, method);
                    break;
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
