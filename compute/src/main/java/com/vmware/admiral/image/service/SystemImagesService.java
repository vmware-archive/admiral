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

package com.vmware.admiral.image.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.FileContentService;

public class SystemImagesService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.SYSTEM_IMAGES;

    public static final String SYSTEM_IMAGE_QUERY_PARAM = "system-image";

    @Override
    public void handleStart(Operation startOp) {
        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        FileUtil.USER_RESOURCES_PATH_VARIABLE))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((res, ex) -> {
                    if (ex == null && res.hasBody()) {
                        ConfigurationState body = res.getBody(ConfigurationState.class);
                        if (body.value != null && !body.value.isEmpty()) {
                            String userResourcesPath = body.value;
                            startExternalContainerLocalImageResourceServices(userResourcesPath);
                        }
                    }
                    startOp.complete();
                }));
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String containerImage = params.remove(SYSTEM_IMAGE_QUERY_PARAM);
        if (containerImage == null || containerImage.isEmpty()) {
            get.fail(new IllegalArgumentException(
                    String.format(
                            "URL parameter '%s' expected with container image name as value.",
                            SYSTEM_IMAGE_QUERY_PARAM)));
            return;
        }

        String path = UriUtils.buildUriPath(
                ManagementUriParts.SYSTEM_IMAGES, containerImage);

        sendRequest(Operation.createGet(this, path)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            handleResourceImage(get, containerImage);
                        } else {
                            get.fail(ex);
                        }
                    } else {
                        get.transferResponseHeadersFrom(op);
                        get.getResponseHeaders().put(Operation.CONTENT_TYPE_HEADER,
                                op.getContentType());
                        get.setBody(op.getBodyRaw());
                        get.setStatusCode(op.getStatusCode());
                        get.complete();
                    }
                }));
    }

    // Fetch the data from resources when the image is not found in user resources
    private void handleResourceImage(Operation get, String containerImage) {
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(containerImage);
        if (resourceAsStream == null) {
            get.setStatusCode(Operation.STATUS_CODE_NOT_FOUND);
            get.complete();
        } else {
            get.getResponseHeaders().put(Operation.CONTENT_TYPE_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM);
            try {
                get.setBody(IOUtils.toByteArray(resourceAsStream));
            } catch (IOException e) {
                get.fail(e);
            }
            get.setStatusCode(Operation.STATUS_CODE_OK);
            get.complete();
        }
    }

    private void startExternalContainerLocalImageResourceServices(String resourcesPath) {
        Path localContainerImagesResourcePath = Paths.get(resourcesPath,
                ManagementUriParts.SYSTEM_IMAGES);

        if (!localContainerImagesResourcePath.toFile().exists()) {
            log(Level.WARNING, FileUtil.getForwardSlashesPathString(localContainerImagesResourcePath) + " does not exist. " +
                    "System images will be loaded from classpath.");
            return;
        }

        List<File> files = FileUtils.findFiles(localContainerImagesResourcePath, new HashSet<>(),
                false);

        for (File f : files) {
            String subPath = f.getAbsolutePath().replace(
                    localContainerImagesResourcePath.toAbsolutePath().toString(), "");

            Path servicePath = Paths.get(ManagementUriParts.SYSTEM_IMAGES, subPath);
            String servicePathString = FileUtil.getForwardSlashesPathString(servicePath);

            Operation post = Operation
                    .createPost(UriUtils.buildUri(getHost(), servicePathString));
            FileContentService fcs = new FileContentService(f);
            getHost().startService(post, fcs);
        }
    }

    /**
     * Helper method for creating URI path to this service
     *
     * @param imageName
     * @return relative to host path
     */
    public static String buildSystemImageUriPath(String imageName) {
        return UriUtils.buildUriPath(SystemImagesService.SELF_LINK,
                "?" + SystemImagesService.SYSTEM_IMAGE_QUERY_PARAM + "=" + imageName);
    }
}
