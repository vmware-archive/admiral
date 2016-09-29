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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.NETWORKS;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockDockerNetworkService extends StatelessService {

    public static final String SELF_LINK = BASE_VERSIONED_PATH + NETWORKS;

    private static final String NETWORK_ID_KEY = "Id";
    private static final String NETWORK_NAME_KEY = "Name";
    private static final String NETWORK_DRIVER_KEY = "Driver";

    private final AtomicInteger idSequence = new AtomicInteger(0);

    public static Map<String, NetworkItem> networksMap = new HashMap<>();

    public MockDockerNetworkService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    /**
     * Basic POJO for mocked networks. For example:
     *
     * [
     * {
     * "Id": "xxx",
     * "Name": "mynet",
     * "Driver": "bridge",
     * }
     * ]
     *
     */
    public static class NetworkItem {

        /**
         * The id of the network.
         */
        public String Id;

        /**
         * The name of the network.
         */
        public String Name;

        /**
         * Driver which network uses. Default is 'bridge'.
         */
        public String Driver;
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.GET) {
            handleGet(op);
        } else if (op.getAction() == Action.POST) {
            handlePost(op);
        } else {
            getHost().failRequestActionNotSupported(op);
        }
    }

    @Override
    public void handleGet(Operation get) {

        String id = UriUtilsExtended.getLastPathSegment(get.getUri().getPath());

        if (NETWORKS.endsWith(id)) {
            // list networks
            get.setBody(networksMap.values());
        } else {
            // get single network
            NetworkItem networkItem = networksMap.get(id);
            if (networkItem != null) {
                get.setBody(networkItem);
            } else {
                get.setStatusCode(Operation.STATUS_CODE_NOT_FOUND);
                get.setBodyNoCloning(null);
            }
        }

        get.complete();
    }

    @Override
    public void handlePost(Operation post) {

        String id = UriUtilsExtended.getLastPathSegment(post.getUri().getPath());

        if (!CREATE.endsWith(id)) {
            getHost().failRequestActionNotSupported(post);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = post.getBody(Map.class);

            AssertUtil.assertNotNull(request.get("Name"), "Name");
            AssertUtil.assertNotNull(request.get("Driver"), "Driver");

            createNetworkName(request);

            NetworkItem networkItem = new NetworkItem();
            networkItem.Id = request.get(NETWORK_ID_KEY).toString();
            networkItem.Name = request.get(NETWORK_NAME_KEY).toString();
            networkItem.Driver = request.get(NETWORK_DRIVER_KEY).toString();

            networksMap.put(networkItem.Id, networkItem);

            post.setBody(request);
            post.complete();

        } catch (Exception e) {
            post.fail(e);
            return;
        }
    }

    public Map<String, Object> createNetworkName(Map<String, Object> body) {

        // create a 64 char long ID
        body.put(NETWORK_ID_KEY, String.format("%064d", idSequence.incrementAndGet()));

        return body;
    }
}
