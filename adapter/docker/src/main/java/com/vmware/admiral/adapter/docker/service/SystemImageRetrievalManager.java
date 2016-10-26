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

public class SystemImageRetrievalManager {

    public static final String SYSTEM_IMAGES_PATH = "/system-images";

    private ServiceHost host;

    private final Object RETRIEVE_LOCK = new Object();

    private Map<String, List<Consumer<byte[]>>> pendingCallbacksByImagePath = new HashMap<>();

    public SystemImageRetrievalManager(ServiceHost host) {
        this.host = host;
    }

    public void retrieveAgentImage(String containerImageFilePath, AdapterRequest adapterRequest,
            Consumer<byte[]> callback) {

        synchronized (RETRIEVE_LOCK) {
            List<Consumer<byte[]>> pendingCallbacks = pendingCallbacksByImagePath
                    .get(containerImageFilePath);
            if (pendingCallbacks == null) {
                pendingCallbacks = new ArrayList<>();
                pendingCallbacksByImagePath.put(containerImageFilePath, pendingCallbacks);
            }

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
                .setReferer(host.getUri())
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
        List<Consumer<byte[]>> pendingCallbacks = null;
        synchronized (RETRIEVE_LOCK) {
            pendingCallbacks = pendingCallbacksByImagePath.remove(containerImageFilePath);
        }

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
                callback.accept(op.getBody(new byte[0].getClass()));
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
}
