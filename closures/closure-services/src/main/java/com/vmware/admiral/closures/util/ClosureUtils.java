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

package com.vmware.admiral.closures.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;

import com.vmware.xenon.common.Utils;

/**
 * Closure utility class
 */
public final class ClosureUtils {

    private static final long BYTES_IN_MB = 1024 * 1024L;

    public static Long toBytes(int megabytes) {
        return megabytes * BYTES_IN_MB;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().length() <= 0;
    }

    public static String calculateHash(String[] envs) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (String e : envs) {
                md.update(e.getBytes("UTF-8"));
            }

            StringBuilder sb = new StringBuilder();
            byte[] digest = md.digest();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            String errMsg =
                    "Unable to calculate execution env. checksum! Reason: " + ex.getMessage();
            logError(errMsg);
            throw new RuntimeException(errMsg);
        }
    }

    public static byte[] loadDockerImageData(String dockerImageName, String folderFilter,
            Class<?> resourceClass) {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try {
            URL dirURL = resourceClass.getResource("/" + folderFilter);
            logInfo("Reading image data %s from: %s ", dockerImageName, dirURL);

            String extractImageName = extractImageName(dockerImageName);
            String folderNameFilter = folderFilter + extractImageName;
            buildTarData(dirURL, folderNameFilter, byteOutputStream);

            return byteOutputStream.toByteArray();
        } catch (Exception ex) {
            Utils.logWarning("Unable to load docker image data! Reason: ", ex);
        } finally {
            try {
                byteOutputStream.close();
            } catch (IOException e) {
                // not interested
            }
        }
        return new byte[] {};
    }

    private static String extractImageName(String dockerImageName) {
        String tagRemoved;
        if (dockerImageName.indexOf(":") <= 0) {
            tagRemoved = dockerImageName;
        } else {
            tagRemoved = dockerImageName.split(":")[0];
        }
        int repoIndex = tagRemoved.indexOf("/");
        if (repoIndex <= 0) {
            return tagRemoved;
        }

        return tagRemoved.substring(repoIndex + 1);
    }

    public static JsonElement toJsonElement(JsonNode node) {
        JsonObject jsObject = new JsonObject();

        Iterator<String> fieldsIterator = node.fieldNames();
        if (!fieldsIterator.hasNext()) {
            if (node.isObject()) {
                return jsObject;
            }
            return getPrimitiveJsonElement(node);
        }

        while (fieldsIterator.hasNext()) {
            String field = fieldsIterator.next();
            JsonNode childNode = node.get(field);
            JsonElement convertedValue = getJsonObjElement(childNode);
            jsObject.add(field, convertedValue);
        }

        return jsObject;
    }

    private static JsonElement toJsonElementArray(JsonNode node) {
        JsonArray jsObjArray = new JsonArray();
        Iterator<JsonNode> iterator = node.iterator();
        while (iterator.hasNext()) {
            JsonNode childNode = iterator.next();
            JsonElement convertedValue = getJsonObjElement(childNode);
            jsObjArray.add(convertedValue);
        }

        return jsObjArray;
    }

    private static JsonElement getJsonObjElement(JsonNode node) {
        if (node.isObject()) {
            return toJsonElement(node);
        } else if (node.isArray()) {
            return toJsonElementArray(node);
        }

        return getPrimitiveJsonElement(node);
    }

    private static JsonElement getPrimitiveJsonElement(JsonNode node) {
        if (node.isNull()) {
            return JsonNull.INSTANCE;
        } else if (node.isNumber()) {
            return new JsonPrimitive(node.numberValue());
        }

        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        return parser.parse(node.asText());
    }

    private static void buildTarData(URL dirURL, String folderNameFilter, OutputStream outputStream)
            throws
            IOException {
        final JarURLConnection jarConnection = (JarURLConnection) dirURL.openConnection();
        final ZipFile jar = jarConnection.getJarFile();
        final Enumeration<? extends ZipEntry> entries = jar.entries();

        try (TarArchiveOutputStream tarArchiveOutputStream = buildTarStream(outputStream)) {
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (!name.startsWith(folderNameFilter)) {
                    // entry in wrong subdir -- don't copy
                    continue;
                }
                TarArchiveEntry tarEntry = new TarArchiveEntry(
                        entry.getName().replaceAll(folderNameFilter, ""));
                try (InputStream is = jar.getInputStream(entry)) {
                    putTarEntry(tarArchiveOutputStream, tarEntry, is, entry.getSize());
                }
            }

            tarArchiveOutputStream.flush();
            tarArchiveOutputStream.close();
        }
    }

    private static void putTarEntry(TarArchiveOutputStream tarOutputStream,
            TarArchiveEntry tarEntry,
            InputStream inStream, long size)
            throws IOException {
        tarEntry.setSize(size);
        tarOutputStream.putArchiveEntry(tarEntry);
        try (InputStream input = new BufferedInputStream(inStream)) {
            long byteRead = copy(input, tarOutputStream);
            logInfo("---- BYTES READ %s ", byteRead);
            tarOutputStream.closeArchiveEntry();
        }
    }

    private static long copy(InputStream from, OutputStream to)
            throws IOException {
        byte[] buf = new byte[4096];
        long total = 0;
        int r;
        while ((r = from.read(buf)) > 0) {
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    private static TarArchiveOutputStream buildTarStream(OutputStream outputStream) throws
            IOException {
        OutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
        bufferedOutputStream = new GzipCompressorOutputStream(bufferedOutputStream);

        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(
                bufferedOutputStream);
        tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        return tarArchiveOutputStream;
    }

    private static void logInfo(String message, Object... values) {
        Utils.log(ClosureUtils.class, ClosureUtils.class.getSimpleName(), Level.INFO, message,
                values);
    }

    private static void logError(String message, Object... values) {
        Utils.log(ClosureUtils.class, ClosureUtils.class.getSimpleName(), Level.SEVERE, message,
                values);
    }
}
