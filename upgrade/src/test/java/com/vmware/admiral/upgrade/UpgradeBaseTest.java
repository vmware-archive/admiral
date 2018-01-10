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

package com.vmware.admiral.upgrade;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitAuthServiceConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.HostInitUpgradeServiceConfig;
import com.vmware.xenon.common.test.VerificationHost;

public abstract class UpgradeBaseTest extends BaseTestCase {
    protected static final String PROJECT_NAME_TEST_PROJECT_1 = "test-project1";

    @Before
    public void beforeForUpgradeBase() throws Throwable {
        startServices(host);
    }

    protected void startServices(VerificationHost host) throws Throwable {
        HostInitCommonServiceConfig.startServices(host);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);
        HostInitAuthServiceConfig.startServices(host);
        HostInitUpgradeServiceConfig.startServices(host);
    }

    protected ProjectState createProject(String name) throws Throwable {
        return createProject(name, null, false, null, null, null, null);
    }

    protected ProjectState createProject(String name, String description, boolean isPublic,
            String adminsGroupLink, String membersGroupLink, String viewersGroupLink,
            Map<String, String> customProperties)
            throws Throwable {
        ProjectState projectState = new ProjectState();

        projectState.id = UUID.randomUUID().toString();
        projectState.name = name;
        projectState.description = description;
        projectState.isPublic = isPublic;
        projectState.administratorsUserGroupLinks = new HashSet<>();
        projectState.membersUserGroupLinks = new HashSet<>();
        projectState.viewersUserGroupLinks = new HashSet<>();
        projectState.customProperties = customProperties;

        if (adminsGroupLink != null) {
            projectState.administratorsUserGroupLinks.add(adminsGroupLink);
        }
        if (membersGroupLink != null) {
            projectState.membersUserGroupLinks.add(membersGroupLink);
        }
        if (viewersGroupLink != null) {
            projectState.viewersUserGroupLinks.add(viewersGroupLink);
        }

        projectState = doPost(projectState, ProjectFactoryService.SELF_LINK);

        return projectState;
    }
}
