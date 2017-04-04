/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.INFO;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants.VERSION;
import static com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants._PING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

public class MockVicHostService extends StatefulService {

    public static final String SELF_LINK = BASE_VERSIONED_PATH;
    public static final String[] childPaths = { INFO, _PING, VERSION };
    public static String NUMBER_OF_CONTAINERS = "11";

    private final List<Service> childServices = new ArrayList<>();

    public MockVicHostService() {
        super(ServiceDocument.class);
    }

    @Override
    public void handleStart(Operation post) {
        // create child endpoints (for version, info ..) using a child service
        if (!isChildService(post)) {
            for (String childPath : childPaths) {
                startChildService(post, childPath, new MockVicHostService());
            }
        }

        post.complete();
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
    public void handleGet(Operation op) {
        String currentPath = op.getUri().getPath();
        if (currentPath.endsWith(INFO)) {
            op.setBody(getInfoBody());
            op.complete();
        } else {
            op.fail(new IllegalArgumentException("Not supported."));
        }
    }

    private Map<String, Object> getInfoBody() {
        Map<String, Object> body = new HashMap<>();

        body.put("Containers", NUMBER_OF_CONTAINERS);
        body.put("CpuCfsPeriod", "true");
        body.put("CpuCfsQuota", "true");
        body.put("DockerRootDir", "/var/lib/docker");
        body.put("HttpProxy", "http://test:test@localhost:8080");
        body.put("HttpsProxy", "https://test:test@localhost:8080");
        body.put("ID", "7TRN:IPZB:QYBB:VPBQ:UMPP:KARE:6ZNR:XE6T:7EWV:PKF4:ZOJD:TPYS");
        body.put("IPv4Forwarding", "true");
        body.put("Images", "16");
        body.put("IndexServerAddress", "https://index.docker.io/v1/");
        body.put("InitPath", "/usr/bin/docker");
        body.put("KernelVersion", "3.12.0-1-amd64");
        body.put("MemTotal", "6979321856");
        body.put("MemoryLimit", "true");
        body.put("NCPU", "1");
        body.put("Name", "prod-server-42");
        body.put("NoProxy", "9.81.1.160");
        body.put("OperatingSystem", "Boot2Docker");
        body.put("SwapLimit", "false");
        body.put("SystemTime", "2015-03-10T11:11:23.730591467-07:00");
        body.put("Driver", "vSphere Integrated Containers");
        List<List<String>> systemStatus = new ArrayList<>();
        systemStatus.add(Arrays.asList("VolumeStores", "default"));
        systemStatus.add(Arrays.asList(" VCH CPU limit", "7500 MHz"));
        systemStatus.add(Arrays.asList(" VCH memory limit", "6.500 GiB"));
        systemStatus.add(Arrays.asList(" VCH CPU usage", "2250 MHz"));
        systemStatus.add(Arrays.asList(" VCH memory usage", "2.500 GiB"));
        body.put("SystemStatus", systemStatus);
        return body;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }
}
