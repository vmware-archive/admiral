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

package com.vmware.admiral;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class ContainerImageIconService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONTAINER_IMAGE_ICONS;
    public static final String CONTAINER_IMAGE_QUERY_PARAM = "container-image";

    private static final String CONTAINER_IMAGE_ICON_CACHE_CONTROL_MAX_AGE = "container.image.icon.cache-control.max-age";

    private static final String CONTIANER_IMAGE_FORMAT = "%s.png";
    private static final String IDENTICON_IMAGE_FORMAT = "identicon-%s.png";
    private static final int IDENTICONS_COUNT = 200;

    private static final String CACHE_CONTROL_HEADER = "cache-control";

    private static final Long CACHE_CONTROL_MAX_AGE = Long.getLong(
            CONTAINER_IMAGE_ICON_CACHE_CONTROL_MAX_AGE, TimeUnit.HOURS.toSeconds(2));

    private static final String CACHE_CONTROL_VALUE = String.format("max-age=%s",
            CACHE_CONTROL_MAX_AGE);

    @Override
    public void authorizeRequest(Operation op) {
        if (ConfigurationUtil.isEmbedded()) {
            op.complete();
            return;
        }
        super.authorizeRequest(op);
    }

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
                            startExternalContainerImageIconResourceServices(userResourcesPath);
                        }
                    }
                    startOp.complete();
                }));
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String containerImageIcon = params.remove(CONTAINER_IMAGE_QUERY_PARAM);
        if (containerImageIcon == null || containerImageIcon.isEmpty()) {
            String errorMsg = String.format(
                    "URL parameter '%s' expected with container image name as value.",
                    CONTAINER_IMAGE_QUERY_PARAM);
            get.fail(new LocalizableValidationException(errorMsg, "ui.container-image.container.name.missing",
                    CONTAINER_IMAGE_QUERY_PARAM));
            return;
        }

        String expectedImageName = String.format(CONTIANER_IMAGE_FORMAT,
                containerImageIcon);
        String expectedImagePath = UriUtils.buildUriPath(
                ManagementUriParts.CONTAINER_ICONS_RESOURCE_PATH, expectedImageName);

        int identiconHash = Math.abs(containerImageIcon.hashCode() % IDENTICONS_COUNT);
        String expectedIdenticonName = String.format(IDENTICON_IMAGE_FORMAT, identiconHash);
        String expectedIdenticonPath = UriUtils.buildUriPath(
                ManagementUriParts.CONTAINER_IDENTICONS_RESOURCE_PATH,
                expectedIdenticonName);

        getIcon(expectedImagePath, get, () -> {
            getIcon(expectedIdenticonPath, get, null);
        });
    }

    private void getIcon(String path, Operation get, Runnable notFoundHandler) {
        Operation getOp = Operation.createGet(this, path)
                .setCompletion((op, ex) -> {
                    if (op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND
                            && notFoundHandler != null) {
                        notFoundHandler.run();
                    } else if (ex != null) {
                        get.fail(ex);
                    } else {
                        get.transferResponseHeadersFrom(op);
                        get.getResponseHeaders().put(Operation.CONTENT_TYPE_HEADER, op.getContentType());
                        get.getResponseHeaders().put(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);
                        get.setBody(op.getBodyRaw());
                        get.setStatusCode(op.getStatusCode());
                        get.complete();
                    }
                });

        String originalUiProxyHeader = get.getRequestHeader(ConfigurationUtil.UI_PROXY_FORWARD_HEADER);
        if (originalUiProxyHeader != null) {
            getOp.addRequestHeader(ConfigurationUtil.UI_PROXY_FORWARD_HEADER, originalUiProxyHeader);
        }

        sendRequest(getOp);
    }

    private void startExternalContainerImageIconResourceServices(String resourcesPath) {

        Path iconResourcePath = Paths.get(resourcesPath,
                ManagementUriParts.CONTAINER_ICONS_RESOURCE_PATH);

        if (!iconResourcePath.toFile().exists()) {
            logInfo("Skip loading container icons from user resource path");
            return;
        } else {
            logInfo("Loading container icons from user resource path");
        }

        List<File> files = FileUtils.findFiles(iconResourcePath, new HashSet<>(), false);

        for (File f : files) {
            String subPath = f.getAbsolutePath().replace(
                    iconResourcePath.toAbsolutePath().toString(), "");

            Path servicePath = Paths
                    .get(ManagementUriParts.CONTAINER_ICONS_RESOURCE_PATH.substring(1), subPath);
            String servicePathString = FileUtil.getForwardSlashesPathString(servicePath);

            Operation post = Operation
                    .createPost(UriUtils.buildUri(getHost(), servicePathString));
            RestrictiveFileContentService fcs = new RestrictiveFileContentService(f);
            getHost().startService(post, fcs);
        }

    }
}
