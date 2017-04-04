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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.JSON;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.LOGS;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.START;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.STATS;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.STOP;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

/**
 * Mock for servicing requests for a container instance
 */
public class MockDockerContainerService extends StatefulService {
    public static final String SELF_LINK = BASE_VERSIONED_PATH + CONTAINERS;
    public static final String[] childPaths = { JSON, START, STOP, LOGS };

    public static class MockDockerContainerState extends ServiceDocument {
        public String Id;
        public String Created;
        public String[] Cmd;
        public String Name;
        public String[] Env;

        public State State = new State();
        public Config Config = new Config();
        public NetworkSettings NetworkSettings = new NetworkSettings();
        public HostConfig HostConfig = new HostConfig();

        public static class State {
            public boolean Running;
            public String StartedAt;
        }

        public static class Config {
            public String Hostname;
            public String[] Cmd;
            public String[] Env;
        }

        public static class NetworkSettings {
            public Map<String, List<PortMapping>> Ports;
        }

        public static class PortMapping {
            public String HostIp;
            public String HostPort;
        }

        public static class HostConfig {
            public Map<String, List<PortMapping>> PortBindings;
            public RestartPolicy RestartPolicy;
        }

        public static class RestartPolicy {
            public String Name;
            public int MaximumRetryCount;
        }
    }

    private final List<Service> childServices = new ArrayList<>();

    public MockDockerContainerService() {
        super(MockDockerContainerState.class);
    }

    @Override
    public void handleStart(Operation post) {
        // create child endpoints (for inspect, start, etc.) using a child
        // service
        if (!isChildService(post)) {
            for (String childPath : childPaths) {
                if (LOGS.equals(childPath)) {
                    startChildService(post, childPath, new MockDockerContainerLogsService());
                } else {
                    startChildService(post, childPath, new MockDockerContainerService());
                }
            }

            // start container stats service
            startChildService(post, STATS, new MockDockerContainerStatsService());
        }

        super.handleStart(post);
    }

    /**
     * Is the current service a child service
     *
     * @return
     */
    private boolean isChildService(Operation op) {
        String path = op.getUri().getPath();
        return !path.equals(getParentPath(op));
    }

    /**
     * Get the path of the parent service
     *
     * @param op
     * @return
     */
    private String getParentPath(Operation op) {
        String path = op.getUri().getPath();
        for (String childPath : childPaths) {
            int index = path.lastIndexOf(childPath);
            if (index > 0) {
                return path.substring(0, index);
            }
        }
        return path;
    }

    private void startChildService(Operation post, String path, Service childService) {
        getHost().startService(
                Operation.createPost(UriUtils.extendUri(post.getUri(), path))
                    .setBody(post.getBodyRaw()), childService
        );

        childServices.add(childService);
    }

    @Override
    public void handleDelete(Operation delete) {
        for (Service childService : childServices) {
            // delete child service as well
            getHost().stopService(childService);
        }
        super.handleDelete(delete);
    }

    @Override
    public void handlePost(Operation post) {
        // handle container start/stop or updates to the state
        MockDockerContainerState state = getState(post);
        String jsonPath = UriUtils.buildUriPath(SELF_LINK, state.Id, JSON);

        String currentPath = post.getUri().getPath();
        if (currentPath.endsWith(START)) {
            // forward the request to update the json state
            state.State.Running = true;
            sendRequest(Operation.createPost(this, jsonPath).setBody(state));

        } else if (currentPath.endsWith(STOP)) {
            // forward the request to update the json state
            state.State.Running = false;
            sendRequest(Operation.createPost(this, jsonPath).setBody(state));

        } else if (currentPath.endsWith(JSON)) {
            state = post.getBody(MockDockerContainerState.class);
            setState(post, state);

        } else if (currentPath.endsWith(LOGS)) {
            // Don't do anything
            System.out.println("LOGS");
        }
        post.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }
}
