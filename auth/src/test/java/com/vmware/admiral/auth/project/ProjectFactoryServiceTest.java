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

package com.vmware.admiral.auth.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ProjectFactoryServiceTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "testName";
    private static final String PROJECT_DESCRIPTION = "testDescription";
    private static final boolean PROJECT_IS_PUBLIC = false;

    private ProjectState project;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_ADMIN);
        doPatch(projectRoles, project.documentSelfLink);
    }

    @Test
    public void testGetStateWithMembers() {
        URI uriWithExpand = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(host, ProjectFactoryService.SELF_LINK),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.toString(true),
                UriUtils.URI_PARAM_ODATA_FILTER, String.format("%s eq '%s'",
                        ServiceDocument.FIELD_NAME_SELF_LINK, project.documentSelfLink));

        host.testStart(1);
        Operation.createGet(uriWithExpand)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
                        try {
                            assertEquals(new Long(1), result.documentCount);

                            assertNotNull(result.documentLinks);
                            assertEquals(1, result.documentLinks.size());
                            assertEquals(project.documentSelfLink, result.documentLinks.iterator().next());

                            assertNotNull(result.documents);
                            assertEquals(1, result.documents.size());
                            assertEquals(project.documentSelfLink, result.documents.keySet().iterator().next());

                            Object jsonProject = result.documents.values().iterator().next();
                            ExpandedProjectState stateWithMembers = Utils.fromJson(jsonProject, ExpandedProjectState.class);
                            assertNotNull(stateWithMembers);
                            assertNotNull(stateWithMembers.administrators);
                            assertEquals(1, stateWithMembers.administrators.size());
                            assertEquals(USER_EMAIL_ADMIN, stateWithMembers.administrators.iterator().next().email);
                            assertNotNull(stateWithMembers.members);
                            assertEquals(1, stateWithMembers.members.size());
                            assertEquals(USER_EMAIL_ADMIN, stateWithMembers.members.iterator().next().email);

                            host.completeIteration();
                        } catch (Throwable ex) {
                            host.failIteration(ex);
                        }
                    }
                }).sendWith(host);
        host.testWait();
    }

}
