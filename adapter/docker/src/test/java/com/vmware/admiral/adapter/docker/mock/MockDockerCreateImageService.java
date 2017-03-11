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

package com.vmware.admiral.adapter.docker.mock;

import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.BASE_VERSIONED_PATH;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.CREATE;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.IMAGES;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_REGISTRY_AUTH;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.adapter.docker.service.DockerAdapterService.AuthConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Mock for servicing container creation requests
 */
public class MockDockerCreateImageService extends StatelessService {
    public static final String SELF_LINK = BASE_VERSIONED_PATH + IMAGES + CREATE;

    public static final String REGISTRY_USER = "testuser";
    public static final String REGISTRY_PASSWORD = "testpassword";

    private boolean expectRegistryAuthHeader;

    public static class Status {
        public String status;
        public Map<String, String> progressDetail;
        public String id;
        public Map<String, String> errorDetail;
        public String error;
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.POST) {
            handlePost(op);

        } else {
            Operation.failActionNotSupported(op);
        }
    }

    /**
     *
     * @param op
     */
    public void handlePost(Operation op) {
        if (expectRegistryAuthHeader) {
            String registryAuthHeader = op.getRequestHeader(DOCKER_IMAGE_REGISTRY_AUTH);
            if (registryAuthHeader == null) {
                op.fail(new IllegalStateException("X-Registry-Auth header expected"));
                return;
            }

            String authConfigDecoded = new String(Base64.getDecoder().decode(registryAuthHeader));
            AuthConfig authConfig = Utils.fromJson(authConfigDecoded, AuthConfig.class);

            assertNotNull(authConfig.auth, "auth");
            assertNotNull(authConfig.email, "email");
            assertNotNull(authConfig.serveraddress, "serveraddress");
            assertTrue(REGISTRY_USER.equals(authConfig.username),
                    "X-Registry-Auth: username does not equal " + REGISTRY_USER);
            assertTrue(REGISTRY_PASSWORD.equals(authConfig.password),
                    "X-Registry-Auth: password does not equal " + REGISTRY_PASSWORD);
        }

        List<Status> response = new ArrayList<>();

        URI requestUri = op.getUri();
        Map<String, String> parameters = UriUtils.parseUriQueryParams(requestUri);
        String fromImage = parameters.get("fromImage");

        if (fromImage == null || fromImage.isEmpty()) {
            response.add(createDownloadingFromStatus("http://"));
            response.add(createErrorNoHost());

        } else {
            response.add(createInitialStatus());
            response.add(createProgressStatus());
            response.add(createDownloadCompleteStatus());
            response.add(createUpToDateStatus());
        }

        StringBuilder sb = new StringBuilder();
        for (Status status : response) {
            sb.append(Utils.toJson(status));
            sb.append("\n");
        }
        op.setBody(sb.toString());
        op.complete();
    }

    public void setExpectRegistryAuthHeader(boolean expectRegistryAuthHeader) {
        this.expectRegistryAuthHeader = expectRegistryAuthHeader;
    }

    /**
     * @return
     */
    private Status createInitialStatus() {
        Status status = new Status();
        status.status = "Pulling repository busybox";

        return status;
    }

    /**
     * @return
     */
    private Status createProgressStatus() {
        Status status = new Status();

        status.status = "Pulling image (ubuntu-12.04) from busybox";
        status.progressDetail = new HashMap<String, String>();
        status.id = "faf804f0e07b";
        return status;
    }

    /**
     *
     * @return
     */
    private Status createDownloadCompleteStatus() {
        Status status = new Status();
        status.status = "Download complete";
        status.id = "faf804f0e07b";
        return status;
    }

    /**
     *
     * @return
     */
    private Status createUpToDateStatus() {
        Status status = new Status();
        status.status = "Status: Image is up to date for busybox";
        return status;
    }

    /**
     *
     * @param from
     * @return
     */
    private Status createDownloadingFromStatus(String from) {
        Status status = new Status();
        status.status = String.format("Downloading from %s", from);
        return status;
    }

    /**
     * @return
     */
    private Status createErrorNoHost() {
        Status status = new Status();
        status.error = "Get http://: http: no Host in request URL";
        status.errorDetail = new HashMap<>();
        status.errorDetail.put("message", "Get http://: http: no Host in request URL");
        return status;
    }

}
