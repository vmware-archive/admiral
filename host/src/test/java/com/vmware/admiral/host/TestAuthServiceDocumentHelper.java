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

package com.vmware.admiral.host;

import java.util.ArrayList;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Generic class which is used for test purposes. It provides ability to have predefine set of
 * ServiceDocuments when authentication in system is enabled and ServiceHost is started.
 *
 */
public class TestAuthServiceDocumentHelper extends AbstractInitialBootService {

    private static final String TEST_DOCUMENTS_INITIALIZER = "/test-documents-initializer";
    public static final String SELF_LINK = ManagementUriParts.CONFIG + TEST_DOCUMENTS_INITIALIZER;
    public static final String TEST_CONTAINER_DESC_SELF_LINK = "test-container-desc";

    @Override
    public void handlePost(Operation post) {
        ArrayList<ServiceDocument> states = new ArrayList<>();
        states.add(
                createContainerDescription());
        initInstances(post, true, states.toArray(new ServiceDocument[states.size()]));
    }

    private ContainerDescription createContainerDescription() {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = UriUtilsExtended
                .buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                        TEST_CONTAINER_DESC_SELF_LINK);
        containerDesc.name = "test-name";
        containerDesc.image = String.format("%s:%s", "test", "latest");
        containerDesc.volumes = new String[] { "/var/run/docker.sock:/var/run/docker.sock",
                "/etc/docker:/etc/docker" };
        containerDesc.restartPolicy = "always";
        containerDesc.networkMode = "host";
        return containerDesc;
    }
}
