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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.FileContentService;

/**
 * Retrieve predefined set of popular images taken from configuration file, first checking for an
 * external file set by the user and if that fails or there is no such file, then reading the
 * internal resource with the default content.
 *
 * Popular images are common to all tenants!
 *
 * Eventually those images will be displayed on the Templates tab when no query is provided.
 */
public class PopularImagesService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.POPULAR_IMAGES;

    private static final String POPULAR_IMAGES_FILE = "/popular-images.json";

    private static final String EXTERNAL_LINK = UriUtils.buildUriPath(SELF_LINK, "external");

    @Override
    public void handleStart(Operation startOp) {
        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        FileUtil.USER_RESOURCES_PATH_VARIABLE))
                .setCompletion((res, ex) -> {
                    if (ex == null && res.hasBody()) {
                        ConfigurationState body = res.getBody(ConfigurationState.class);
                        if (body.value != null && !body.value.isEmpty()) {
                            String userResourcesPath = body.value;
                            startExternalPopularImagesService(userResourcesPath);
                        }
                    }
                    startOp.complete();
                }));
    }

    private void startExternalPopularImagesService(String resourcesPath) {

        Path filePath = Paths.get(resourcesPath, POPULAR_IMAGES_FILE);

        if (!filePath.toFile().exists()) {
            logInfo("Skip loading popular images from user resource path.");
            return;
        } else {
            logInfo("Loading popular images from user resource path...");
        }

        File file = filePath.toFile();

        try {
            Utils.fromJson(FileUtil.getResourceAsString(file.getAbsolutePath(), false),
                    Collection.class);
        } catch (Exception e) {
            logWarning("Error validating popular images file content: %s", e.getMessage());
            return;
        }

        Operation post = Operation.createPost(UriUtils.buildUri(getHost(), EXTERNAL_LINK));
        FileContentService fcs = new FileContentService(file);
        getHost().startService(post, fcs);
    }

    @Override
    public void handleGet(Operation get) {
        getExternalPopularImages(get, () -> {
            getDefaultPopularImages(get);
        });
    }

    private void getExternalPopularImages(Operation get, Runnable notFoundHandler) {
        sendRequest(Operation.createGet(UriUtils.buildUri(getHost(), EXTERNAL_LINK))
                .setCompletion((op, ex) -> {
                    if (ex != null || op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        notFoundHandler.run();
                    } else {
                        get.setBody(op.getBodyRaw());
                        get.setStatusCode(op.getStatusCode());
                        get.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
                        get.complete();
                    }
                }));
    }

    private void getDefaultPopularImages(Operation get) {
        get.setBody(FileUtil.getResourceAsString(POPULAR_IMAGES_FILE, true));
        get.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        get.complete();
    }
}
