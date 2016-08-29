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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.CONTAINERS;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.CREATE;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.adapter.docker.mock.MockDockerContainerListService.ContainerItem;
import com.vmware.admiral.adapter.docker.mock.MockDockerContainerService.MockDockerContainerState.State;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Mock for servicing container creation requests
 *
 * This is not using the standard DCP factory pattern because docker uses a different URL naming
 * scheme (adds /create at the end of the path)
 */
public class MockDockerCreateContainerService extends StatelessService {
    public static final String SELF_LINK = BASE_VERSIONED_PATH + CONTAINERS + CREATE;

    private final AtomicInteger idSequence = new AtomicInteger();

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.POST) {
            handlePost(op);

        } else {
            getHost().failRequestActionNotSupported(op);
        }
    }

    /**
     *
     * @param op
     */
    public void handlePost(Operation op) {
        @SuppressWarnings("unchecked")
        Map<String, Object> request = op.getBody(Map.class);

        try {
            MockDockerContainerService serviceInstance = new MockDockerContainerService();
            Map<String, Object> response = createContainer(request);

            String id = (String) response.get("Id");
            request.put("Id", id);
            request.put("Created", "2015-06-19T22:08:21.206214158Z");

            State state = new State();
            state.StartedAt = "2015-06-19T22:08:21.206214158Z";
            request.put("State", state);
            request.put("Cmd", new String[] { "cat" });

            processRequest(request);

            getHost().startService(
                    Operation.createPost(
                            this, UriUtils.buildUriPath(MockDockerContainerService.SELF_LINK, id))
                            .setBody(request), serviceInstance);

            ContainerItem containerItem = new ContainerItem();
            containerItem.Id = id;
            MockDockerContainerListService.containerList.add(containerItem);

            op.setBody(response);
            op.complete();

        } catch (Exception x) {
            op.fail(x);
            return;
        }
    }

    /**
     * perform the container creation and build the response
     *
     * @param request
     * @return
     */
    public Map<String, Object> createContainer(Map<String, Object> request) {

        AssertUtil.assertNotNull(request.get("Cmd"), "Cmd");

        Map<String, Object> response = new HashMap<>();

        // create a 64 char long ID
        response.put("Id", String.format("%064d", idSequence.incrementAndGet()));

        return response;
    }

    /**
     * Move request properties to the Config object before storing the state
     *
     * @param request
     */
    private void processRequest(Map<String, Object> request) {
        Map<String, Object> config = new HashMap<>();
        request.put("Config", config);
        config.put("Hostname", request.remove("Hostname"));
        config.put("Cmd", request.remove("Cmd"));
        config.put("Env", request.remove("Env"));

        @SuppressWarnings("unchecked")
        Map<String, Object> hostConfig = (Map<String, Object>) request.get("HostConfig");
        if (hostConfig == null) {
            hostConfig = new HashMap<>();
            request.put("HostConfig", hostConfig);
        }

        Map<String, String> portMapping = new HashMap<>();
        portMapping.put("HostIp", "0.0.0.0");
        portMapping.put("HostPort", "9999");
        List<Map<String, String>> portMappings = Collections.singletonList(portMapping);
        Map<String, List<Map<String, String>>> allPortMappings = new HashMap<>();
        allPortMappings.put("8080/tcp", portMappings);

        hostConfig.put("PortBindings", allPortMappings);

        Map<String, Object> networkSettings = new HashMap<>();
        request.put("NetworkSettings", networkSettings);
        networkSettings.put("Ports", allPortMappings);
    }
}
