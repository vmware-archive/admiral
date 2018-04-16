/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.upgrade.UpgradeBaseTest;

public class ProjectTransformationBootstrapServiceTest extends UpgradeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
    }

    @Test
    public void testProjectSelfLinkShouldBeRemovedFromTenantLinksSingleProject() throws Throwable {
        ProjectState projectState = new ProjectState();
        projectState.name = "test-project";

        projectState = doPost(projectState, ProjectFactoryService.SELF_LINK);

        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");
        tenantLinks.add(projectState.documentSelfLink);
        projectState.tenantLinks = tenantLinks;

        doPatch(projectState, projectState.documentSelfLink);

        host.registerForServiceAvailability(ProjectsTransformationBootstrapService.startTask(host),
                true,
                ProjectsTransformationBootstrapService.FACTORY_LINK);

        String projectSelfLink = projectState.documentSelfLink;
        waitFor(() -> {
            ProjectState state = getDocument(ProjectState.class, projectSelfLink);
            return !state.tenantLinks.contains(state.documentSelfLink);
        });
    }

    @Test
    public void testProjectSelfLinkShouldBeRemovedFromTenantLinksMultipleProjects()
            throws Throwable {
        ProjectState firstState = new ProjectState();
        firstState.name = "test-project1";

        ProjectState secondState = new ProjectState();
        secondState.name = "test-project2";

        ProjectState thirdState = new ProjectState();
        thirdState.name = "test-project3";

        firstState = doPost(firstState, ProjectFactoryService.SELF_LINK);
        secondState = doPost(secondState, ProjectFactoryService.SELF_LINK);
        thirdState = doPost(thirdState, ProjectFactoryService.SELF_LINK);

        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add(firstState.documentSelfLink);
        tenantLinks.add(secondState.documentSelfLink);
        tenantLinks.add(thirdState.documentSelfLink);

        firstState.tenantLinks = tenantLinks;
        secondState.tenantLinks = tenantLinks;

        doPatch(firstState, firstState.documentSelfLink);
        doPatch(secondState, secondState.documentSelfLink);
        doPatch(thirdState, thirdState.documentSelfLink);

        host.registerForServiceAvailability(ProjectsTransformationBootstrapService.startTask(host),
                true,
                ProjectsTransformationBootstrapService.FACTORY_LINK);

        String firstSelfLink = firstState.documentSelfLink;
        String secondSelfLink = secondState.documentSelfLink;
        String thirdSelfLink = thirdState.documentSelfLink;

        waitFor(() -> {
            ProjectState first = getDocument(ProjectState.class, firstSelfLink);
            ProjectState second = getDocument(ProjectState.class, secondSelfLink);
            ProjectState third = getDocument(ProjectState.class, thirdSelfLink);

            return !first.tenantLinks.contains(first.documentSelfLink)
                    && !second.tenantLinks.contains(second.documentSelfLink)
                    && !third.tenantLinks.contains(third.documentSelfLink);
        });
    }
}
