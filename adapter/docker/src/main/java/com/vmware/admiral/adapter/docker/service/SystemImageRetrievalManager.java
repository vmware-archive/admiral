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

package com.vmware.admiral.adapter.docker.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class SystemImageRetrievalManager {

    public static final String SERVICE_REFERRER_PATH = "/system-image-retrieval-manager";
    public static final String SYSTEM_IMAGES_PATH = "/system-images";

    private ServiceHost host;

    private final Object RETRIEVE_LOCK = new Object();

    private Map<String, List<Consumer<byte[]>>> pendingCallbacksByImagePath = new HashMap<>();

    /**
     * Map to keep a reference to loaded system images. Once loaded, the data will be added using
     * the image file path as a key and time of the last usage will be stored. The timestamp is
     * global for all the images. Once the defined timeout expires all the images will be cleared.
     */
    private static Map<String, byte[]> cachedImages = new HashMap<>();
    private static long lastUsed;
    private static final long CACHED_DATA_MICROS = Integer.getInteger(
            "com.vmware.admiral.system.image.cache.micros",
            (int) TimeUnit.SECONDS.toMicros(60));

    public SystemImageRetrievalManager(ServiceHost host) {
        this.host = host;
    }

    public void retrieveAgentImage(String containerImageFilePath, AdapterRequest adapterRequest,
            Consumer<byte[]> callback) {

        synchronized (RETRIEVE_LOCK) {
            byte[] imageData = cachedImages.get(containerImageFilePath);
            if (imageData != null) {
                host.log(Level.INFO, "Cached image found, %s\n", containerImageFilePath);
                lastUsed = Utils.getSystemNowMicrosUtc();
                callback.accept(imageData);
                return;
            }

            List<Consumer<byte[]>> pendingCallbacks = pendingCallbacksByImagePath
                    .computeIfAbsent(containerImageFilePath, k -> new ArrayList<>());

            pendingCallbacks.add(callback);

            if (pendingCallbacks.size() > 1) {
                // someone already triggered retrieval.
                return;
            }
        }

        URI propsUri = adapterRequest.resolve(UriUtils.buildUriPath(
                ManagementUriParts.CONFIG_PROPS, FileUtil.USER_RESOURCES_PATH_VARIABLE));

        host.sendRequest(Operation
                .createGet(propsUri)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((res, ex) -> {
                    String userResourcesPath = null;
                    if (ex == null && res.hasBody()) {
                        ConfigurationState body = res.getBody(ConfigurationState.class);
                        if (body.value != null && !body.value.isEmpty()) {
                            userResourcesPath = body.value;
                        }
                    }
                    retrieveAgentImage(userResourcesPath, containerImageFilePath);
                }));
    }

    private void notifyCallbacks(String containerImageFilePath, byte[] imageData) {
        List<Consumer<byte[]>> pendingCallbacks;
        synchronized (RETRIEVE_LOCK) {
            cachedImages.put(containerImageFilePath, imageData);
            lastUsed = Utils.getSystemNowMicrosUtc();
            pendingCallbacks = pendingCallbacksByImagePath.remove(containerImageFilePath);
        }
        host.log(Level.INFO, "Caching system agent image data for %s", containerImageFilePath);
        host.schedule(this::cleanCache, CACHED_DATA_MICROS, TimeUnit.MICROSECONDS);

        if (pendingCallbacks != null) {
            for (Consumer<byte[]> consumer : pendingCallbacks) {
                consumer.accept(imageData);
            }
        }
    }

    private void retrieveAgentImage(String resourcesPath, String containerImageFilePath) {
        Consumer<byte[]> finalCallback = (fileBytes) -> {
            if (fileBytes == null) {
                host.log(Level.WARNING, "System image " + containerImageFilePath
                        + " does not exists.");
            }
            notifyCallbacks(containerImageFilePath, fileBytes);
        };

        if (resourcesPath != null) {
            getExternalAgentImage(resourcesPath, containerImageFilePath, (fileBytes) -> {
                if (fileBytes != null) {
                    notifyCallbacks(containerImageFilePath, fileBytes);
                } else {
                    // Fetch the data from resources when the image is not found in user resources
                    getResourceAgentImage(containerImageFilePath, finalCallback);
                }
            });
        } else {
            getResourceAgentImage(containerImageFilePath, finalCallback);
        }
    }

    private void getExternalAgentImage(String resourcesPath, String containerImage,
            Consumer<byte[]> callback) {
        Path imageResourcePath = Paths.get(resourcesPath,
                SYSTEM_IMAGES_PATH, containerImage);

        File file = imageResourcePath.toFile();
        if (!file.exists()) {
            callback.accept(null);
            return;
        }

        Operation operation = new Operation();
        operation.setCompletion((op, ex) -> {
            if (op.hasBody()) {
                callback.accept(op.getBody(byte[].class));
            } else {
                callback.accept(null);
            }

        });

        FileUtils.readFileAndComplete(operation, file);
    }

    private void getResourceAgentImage(String containerImage, Consumer<byte[]> callback) {
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(containerImage);
        if (resourceAsStream == null) {
            callback.accept(null);
        } else {
            try {
                callback.accept(IOUtils.toByteArray(resourceAsStream));
            } catch (IOException e) {
                callback.accept(null);
            }
        }
    }

    private void cleanCache() {
        if (lastUsed + CACHED_DATA_MICROS < Utils.getSystemNowMicrosUtc()) {
            // expired, clean the reference
            cachedImages.clear();
            lastUsed = 0;
            host.log(Level.INFO, "System image(s) removed from cache");
        } else {
            // schedule next check
            host.schedule(this::cleanCache, CACHED_DATA_MICROS, TimeUnit.MICROSECONDS);
        }
    }

}
