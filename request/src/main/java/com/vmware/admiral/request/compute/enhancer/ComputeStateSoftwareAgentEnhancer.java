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

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.WRITE_FILES_ELEMENT;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.loadResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.request.compute.enhancer.EnhancerUtils.WriteFiles;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;

public class ComputeStateSoftwareAgentEnhancer extends ComputeEnhancer {
    private static final String ENABLE_SOFTWARE_MANAGEMENT_FIELD_ID = "Compute.EnableSoftwareManagement";
    private static final String SOFTWARE_PROP_PREFIX = "__software.";

    private String bootstrapContent;

    public ComputeStateSoftwareAgentEnhancer() {
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context, ComputeState cs) {

        DeferredResult<ComputeState> result = new DeferredResult<>();
        if (enableSoftwareManagement(cs)) {
            try {
                applySoftwareServiceConfig(context, cs);
            } catch (IOException e) {
                return DeferredResult.failed(e);
            }
        } else {
            result.complete(cs);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void applySoftwareServiceConfig(EnhanceContext context, ComputeState cs)
            throws IOException {
        Map<String, Object> content = context.content;
        if (content == null) {
            content = new HashMap<>();
        }

        String softwareProps = cs.customProperties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(SOFTWARE_PROP_PREFIX))
                .map(e -> cleanKey(e.getKey()) + "=" + e.getValue())
                .collect(Collectors.joining("\n")) + "\n";

        List<Object> list = (List<Object>) content.get(WRITE_FILES_ELEMENT);
        if (list == null) {
            list = new ArrayList<>();
        }

        list.add(new WriteFiles("/opt/vmware/agent/agent_bootstrap.sh", "0755",
                getBootstrapContent()));
        list.add(new WriteFiles("/opt/vmware/agent/appd.properties", "0644", softwareProps));
        content.put(WRITE_FILES_ELEMENT, list);

        context.content = content;

        // List<Object> runcmds = (List<Object>) content.get(RUNCMD_ELEMENT);
        // if (runcmds == null) {
        // runcmds = new ArrayList<>();
        // }
        // runcmds.add("cd /opt/vmware/agent && sudo ./agent_bootstrap.sh");
        // content.put(RUNCMD_ELEMENT, runcmds);

        // TODO: enable systemd load
        // StringBuilder sb = new StringBuilder("#cloud-config\n");
        // sb.append(objectMapper().writeValueAsString(content));
        // sb.append("\n");
        // sb.append(getSystemDContent());
        // return sb.toString();
    }

    private String getBootstrapContent() throws IOException {
        if (this.bootstrapContent == null) {
            this.bootstrapContent = loadResource("/agent/agent_bootstrap.sh");
        }
        return this.bootstrapContent;
    }

    @SuppressWarnings("unused")
    private String getSystemDContent() throws IOException {
        return loadResource("/agent/coreos_systemd.yaml");
    }

    private String cleanKey(String key) {
        return key.substring(SOFTWARE_PROP_PREFIX.length());
    }

    private boolean enableSoftwareManagement(ComputeState cs) {
        return Boolean.parseBoolean(
                EnhancerUtils.getCustomProperty(cs, ENABLE_SOFTWARE_MANAGEMENT_FIELD_ID));
    }

}
