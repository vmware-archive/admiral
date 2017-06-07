/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jdk.nashorn.internal.ir.WithNode;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.vmware.admiral.closure.runtime.Context;

/**
 * The class is responsible for executing user's java code inside a container.
 *
 */
public class AppRunner {

    private static final String TOKEN = System.getenv("TOKEN");
    private static final String CLOSURE_URI = System.getenv("TASK_URI");

    private static final String SRC_DIR = "./user_scripts";
    private static final String TRUSTED_CERTS = "/app/trust.pem";
    private static final String SRC_FILE_ZIP = "script.zip";
    private static final String SOURCE_URL = "sourceURL";
    private static final String GET = "GET";

    private static final String STATE_STARTED = "STARTED";
    private static final String STATE_FINISHED = "FINISHED";
    private static final String STATE_FAILED = "FAILED";

    private static final int BUFFER_SIZE = 10 * 1024;

    private CloseableHttpClient client;

    public AppRunner() {
        client = createHttpClient();
    }

    public static void main(String[] args) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String currentTime = dateFormat.format(new Date());
        System.out.format("Script run started at: %s%n", currentTime);

        AppRunner closureRunner = new AppRunner();
        try {
            closureRunner.proceedWithClosureExecution();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closureRunner.closeClient();
        }
    }

    private void closeClient() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ex) {
            // not used
        }
    }

    public void saveSourceInFile(JsonObject closureDescription, String moduleName) {
        try {
            if (!new File(SRC_DIR).isDirectory()) {
                Files.createDirectories(Paths.get(SRC_DIR));
            }
            String sourceContent = closureDescription.get("source").getAsString();
            FileWriter writer = new FileWriter(SRC_DIR + File.separator + normalizedModulename
                    (moduleName) + ".java");
            try (BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
                bufferedWriter.write(sourceContent);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Unable to save source file:");
            e.printStackTrace();
        }
    }

    public static void setHeaders(HttpRequest request) {
        request.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());
        request.setHeader("Accept", ContentType.APPLICATION_JSON.toString());
        request.setHeader("x-xenon-auth-token", TOKEN);
    }

    public void executeSource(JsonObject inputs, String closureSemaphore, String moduleName,
            String handlerName, boolean skipCompilation) {
        System.out.println("Script run logs:");
        System.out.println("*******************");
        try {
            Path path = Paths.get("");
            Path currentDir = path.toAbsolutePath();
            Path filePath = currentDir.resolve(SRC_DIR);

            Context context = new ContextImpl(CLOSURE_URI, closureSemaphore, inputs);

            File file = null;
            String targetClass = normalizedModulename(moduleName);
            if (skipCompilation) {
                file = new File(SRC_FILE_ZIP);
            } else {
                String[] cmd = { "/bin/sh", "-c",
                        "javac -cp gson*.jar -sourcepath ./ $(find ./user_scripts/* | grep .java)"
                };
                runProcess(cmd, closureSemaphore);
                file = new File("user_scripts/");
            }
            // Load
            URL url = file.toURI().toURL();
            ClassLoader classLoader = new URLClassLoader(new URL[] { url });
            Class loadedClass = classLoader.loadClass(targetClass);
            Constructor constructor = loadedClass.getConstructor();
            Object object = constructor.newInstance();
            Method method = loadedClass.getMethod(handlerName, Context.class);
            // Invoke
            method.invoke(object, context);
            System.out.println("*******************");
            patchFinishedState(context.getOutputsAsString(), closureSemaphore);
        } catch (Exception ex) {
            System.out.println("*******************");
            System.out.println("Script run failed with: " + ex);
            ex.printStackTrace();
            patchFailedState(closureSemaphore, ex.toString());
        } finally {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            Date date = new Date();
            String currentTime = dateFormat.format(date);
            System.out.format("Script run completed at: %s%n", currentTime);
        }
    }

    private String normalizedModulename(String moduleName) {
        int packageIndex = moduleName.lastIndexOf(".");
        if (packageIndex < 0) {
            return firstCharToUpper(moduleName);
        }
        String packageName = moduleName.substring(0, packageIndex);
        String className = moduleName.substring(packageIndex + 1);
        return packageName + "." + firstCharToUpper(className);
    }

    private String firstCharToUpper(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClientBuilder.create().build();
    }

    public void printOutput(InputStream input) {
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runProcess(String[] command, String closureSemaphore) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            printOutput(process.getInputStream());
            process.waitFor();
            if (process.exitValue() != 0) {
                System.out.println("*******************");
                System.out.println("Script run failed with: ");
                printOutput(process.getErrorStream());
                patchFailedState(closureSemaphore, "Failed to compile the code!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String buildClosureDescriptionUri(String closureDescLink) {
        String pattern = "/resources/closures/";
        String uriHead = CLOSURE_URI.split(pattern)[0];
        return uriHead + closureDescLink;
    }

    public HttpURLConnection getContentOfZipFile(String sourceUrl) {
        HttpURLConnection connection = null;
        try {
            if (!new File(SRC_DIR).isDirectory()) {
                Files.createDirectories(Paths.get(SRC_DIR));
            }
            URL sourceURL = new URL(sourceUrl);
            connection = (HttpURLConnection) sourceURL.openConnection();
            connection.setRequestMethod(GET);
            try (InputStream input = connection.getInputStream();
                    FileOutputStream output = new FileOutputStream(SRC_FILE_ZIP)) {
                byte[] buffer = new byte[2048];
                int n = input.read(buffer);
                while (n >= 0) {
                    output.write(buffer, 0, n);
                    n = input.read(buffer);
                }
                output.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connection;
    }

    public String downloadAngGetContentType(String sourceUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = getContentOfZipFile(sourceUrl);
            int response = connection.getResponseCode();
            if (response != 200) {
                String message = "Unable to fetch script source from: " + sourceUrl;
                System.err.println(message);
                throw new Exception(message);
            }

            return connection.getHeaderField(HttpHeaders.CONTENT_TYPE);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }

    public void unzipSourceCode() throws Exception {
        File src = new File(SRC_DIR);
        if (!src.exists()) {
            src.mkdir();
        }
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream
                (SRC_FILE_ZIP))) {
            ZipEntry entry = zipInput.getNextEntry();
            while (entry != null) {
                String fileName = entry.getName();
                File newFile = new File(SRC_DIR + File.separator + fileName);

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    String fileParent = newFile.getParent();
                    if (!new File(fileParent).exists()) {
                        Files.createDirectories(Paths.get(fileParent));
                    }

                    try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                        int len;
                        byte[] buffer = new byte[BUFFER_SIZE];
                        while ((len = zipInput.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    }
                }
                zipInput.closeEntry();
                entry = zipInput.getNextEntry();
            }
        }
    }

    public String[] createEntryPoint(JsonObject closureDescription) throws Exception {
        String handlerName = closureDescription.get("name").getAsString();
        String[] defaultEntrypoint = { handlerName, handlerName };
        if (closureDescription.has("entrypoint")) {
            String entryPointStr = closureDescription.get("entrypoint").getAsString();
            if (entryPointStr.isEmpty()) {
                return defaultEntrypoint;
            }

            return computeEntrypoint(entryPointStr);
        } else {
            System.out.println("Entry point is empty. Will use closure name for a handler name: "
                    + handlerName);
            return defaultEntrypoint;
        }
    }

    private String[] computeEntrypoint(String entrypointStr) throws Exception {
        int lastDotIndex = entrypointStr.lastIndexOf(".");
        if (lastDotIndex < 0) {
            throw new Exception("Unexpected entrypoint format: " + entrypointStr);
        }

        String packageName = entrypointStr.substring(0, lastDotIndex).trim();
        String methodName = entrypointStr.substring(lastDotIndex + 1).trim();

        return new String[] { packageName, methodName };
    }

    public HttpResponse getClosureContent(String closureDescUri) {
        HttpGet get = new HttpGet(closureDescUri);
        try {
            setHeaders(get);
            return client.execute(get);
        } catch (Exception e) {
            e.printStackTrace();
            get.abort();
        }
        return null;
    }

    public String getResponseContent(HttpResponse response) {
        StringBuilder responseContent = new StringBuilder();
        try {
            InputStream inStream = response.getEntity().getContent();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inStream))) {
                String line = "";
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responseContent.toString();
    }

    public void proceedWithClosureDescription(String closureDescUri, JsonObject inputs,
            String closureSemaphore) {
        HttpResponse response = getClosureContent(closureDescUri);
        String responseContent = getResponseContent(response);
        int closureDescResponse = response.getStatusLine().getStatusCode();
        try {
            if (closureDescResponse == 200) {
                Gson gson = new Gson();
                JsonObject closureDescJson = gson
                        .fromJson(responseContent.toString(), JsonObject.class);
                String[] moduleAndIndexNames = createEntryPoint(closureDescJson);
                String moduleName = moduleAndIndexNames[0];
                String handlerName = moduleAndIndexNames[1];

                if (!closureDescJson.has(SOURCE_URL) || getSourceUrl(closureDescJson).isEmpty()) {
                    saveSourceInFile(closureDescJson, moduleName);
                    executeSource(inputs, closureSemaphore, moduleName, handlerName, false);
                    return;
                }

                String contentType = downloadAngGetContentType(getSourceUrl(closureDescJson));
                handleExternalSourceUrl(inputs, closureSemaphore, moduleName, handlerName,
                        contentType);
            } else {
                String message = "Unable to get closure description from URI: " + CLOSURE_URI + ""
                        + " Reason: "
                        + closureDescResponse;
                System.err.println(message);
                patchFailedState(closureSemaphore, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
            patchFailedState(closureSemaphore, e.getMessage());
        }
    }

    private String getSourceUrl(JsonObject jsonObj) {
        return jsonObj.get(SOURCE_URL).getAsString();
    }

    private void handleExternalSourceUrl(JsonObject inputs, String closureSemaphore, String
            moduleName, String handlerName, String contentType) throws Exception {
        if (contentType.equals("application/zip") || contentType
                .equals("application/octet-stream")) {
            System.out.println("Processing ZIP source file...");
            // save, compile and execute
            unzipSourceCode();
            executeSource(inputs, closureSemaphore, moduleName, handlerName, false);
        } else if (contentType.equals("application/java-archive")) {
            // jar file
            System.out.println("Processing JAR file...");
            // load and execute
            executeSource(inputs, closureSemaphore, moduleName, handlerName, true);
        } else {
            // not supported
            String message = "Not supported code resource format: " + contentType;
            System.err.println(message);
            throw new Exception(message);
        }
    }

    public void patchClosureStarted(String closureSemaphore) throws Exception {
        String data = String.format("{\"state\": %s, \"closureSemaphore\": %s}", STATE_STARTED,
                closureSemaphore);
        HttpPatch patch = null;
        try {
            patch = createHttpPatch(data);
            HttpResponse response = client.execute(patch);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String errorMsg = "Unable to start closure from URI: " + CLOSURE_URI + " Status "
                        + "code: " + statusCode;
                throw new Exception(errorMsg);
            }
        } catch (Exception ex) {
            handlePatchException(patch, STATE_STARTED, ex.getMessage());
        }
    }

    public void patchFinishedState(String outputs, String closureSemaphore) throws Exception {
        String data = String.format("{\"state\": %s, \"closureSemaphore\": %s, \"outputs\": %s}",
                STATE_FINISHED, closureSemaphore, outputs);
        HttpPatch patch = null;
        try {
            patch = createHttpPatch(data);
            HttpResponse response = client.execute(patch);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                System.out.println("Script run state: " + STATE_FINISHED);
            } else {
                throw new Exception("Unable to patch result of closure with URI: " + CLOSURE_URI
                        + " status code: " + statusCode);
            }
        } catch (Exception ex) {
            handlePatchException(patch, STATE_FINISHED, ex.getMessage());
            throw ex;
        }
    }

    public void patchFailedState(String closureSemaphore, String error) {
        String data = String.format("{\"state\": %s,\"closureSemaphore\": %s, \"errorMsg\": %s}",
                STATE_FAILED, closureSemaphore, error);
        HttpPatch patch = null;
        try {
            patch = createHttpPatch(data);
            HttpResponse response = client.execute(patch);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                System.out.println("Unable to patch failed state: " + statusCode);
            }
        } catch (Exception ex) {
            handlePatchException(patch, STATE_FAILED, ex.getMessage());
        }
    }

    private void handlePatchException(HttpPatch patch, String state, String errorMsg) {
        System.out.println("Exception while patching closure with state: " + state + " Reason: " +
                errorMsg);
        if (patch != null) {
            patch.abort();
        }
    }

    private static HttpPatch createHttpPatch(String data) throws UnsupportedEncodingException {
        HttpPatch patch = new HttpPatch(CLOSURE_URI);
        setHeaders(patch);
        patch.setEntity(new StringEntity(data));
        return patch;
    }

    public boolean isBlank(String str) {
        str = str.trim();
        return str.isEmpty() || str == null;
    }

    public void proceedWithClosureExecution() throws Exception {
        if (isBlank(CLOSURE_URI)) {
            System.out.println("TASK_URI environment variable is not set. Aborting...");
            return;
        }

        HttpResponse response = getClosureContent(CLOSURE_URI);
        String responseContent = getResponseContent(response);
        int closureResponse = response.getStatusLine().getStatusCode();

        JsonObject closureInputs;
        String closureSemaphore = null;
        if (closureResponse == 200) {
            Gson gson = new Gson();
            JsonObject closureData = gson
                    .fromJson(responseContent.toString(), JsonObject.class);
            closureSemaphore = closureData.get("closureSemaphore").getAsString();

            patchClosureStarted(closureSemaphore);

            closureInputs = (JsonObject) closureData.get("inputs");

            String closureDescLink = closureData.get("descriptionLink").getAsString();
            String closureDescUri = buildClosureDescriptionUri(closureDescLink);
            proceedWithClosureDescription(closureDescUri, closureInputs, closureSemaphore);
        } else {
            String message = "Unable to get closure data from URI: " + CLOSURE_URI + " Reason: "
                    + closureResponse;
            patchFailedState(closureSemaphore, message);
        }
    }

}