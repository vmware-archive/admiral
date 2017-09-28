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

package com.vmware.admiral.service.test;

import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

public class MockDockerContainerToHostService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.MOCK_CONTAINERS;

    public static class MockDockerContainerToHostState extends ServiceDocument {
        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_IMAGE = "image";
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String parentLink;

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String id;

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String name;

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String image;

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public PowerState powerState;

        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> tenantLinks;
    }

    public MockDockerContainerToHostService() {
        super(MockDockerContainerToHostState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        MockDockerContainerToHostState currentState = getState(patch);
        MockDockerContainerToHostState patchBody = patch
                .getBody(MockDockerContainerToHostState.class);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);
        patch.setBody(currentState).complete();
    }
}
