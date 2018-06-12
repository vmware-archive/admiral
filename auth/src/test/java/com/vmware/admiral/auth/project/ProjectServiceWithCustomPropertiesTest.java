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
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class ProjectServiceWithCustomPropertiesTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "test-name";
    private static final String PROJECT_DESCRIPTION = "testDescription";
    private static final boolean PROJECT_IS_PUBLIC = false;

    private static final String CUSTOM_PROP_KEY_A = "customPropertyA";
    private static final String CUSTOM_PROP_VAL_A = "valueA";
    private static final String CUSTOM_PROP_KEY_B = "customPropertyB";
    private static final String CUSTOM_PROP_VAL_B = "valueB";

    private ProjectState project;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        Map<String, String> customProperties = createCustomPropertiesMap(CUSTOM_PROP_KEY_A,
                CUSTOM_PROP_VAL_A, CUSTOM_PROP_KEY_B, CUSTOM_PROP_VAL_B);
        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC,
                customProperties);
    }

    @Test
    public void testCreateProjectWithCustomProperties() throws Throwable {
        // project is created in the setUp method
        assertNotNull(project);
        assertEquals(PROJECT_NAME, project.name);
        assertEquals(PROJECT_DESCRIPTION, project.description);
        assertEquals(PROJECT_IS_PUBLIC, project.isPublic);
        assertNotNull(project.customProperties);
        assertEquals(3, project.customProperties.size());
        assertEquals(CUSTOM_PROP_VAL_A, project.customProperties.get(CUSTOM_PROP_KEY_A));
        assertEquals(CUSTOM_PROP_VAL_B, project.customProperties.get(CUSTOM_PROP_KEY_B));
    }

    @Test
    public void testSetCustomPropertyOnExistingProject() throws Throwable {
        project = createProject("test-name1");

        ProjectState patchState = new ProjectState();
        patchState.customProperties = createCustomPropertiesMap(CUSTOM_PROP_KEY_A,
                CUSTOM_PROP_VAL_A);

        project = doPatch(patchState, project.documentSelfLink);
        assertNotNull(project.customProperties);
        assertEquals(2, project.customProperties.size());
        assertEquals(CUSTOM_PROP_VAL_A, project.customProperties.get(CUSTOM_PROP_KEY_A));
    }

    @Test
    public void testUpdateProjectCustomProperty() throws Throwable {
        final String updatedValA = CUSTOM_PROP_VAL_A + "-updated";

        ProjectState patchState = new ProjectState();
        patchState.customProperties = createCustomPropertiesMap(CUSTOM_PROP_KEY_A, updatedValA);

        ProjectState updatedProject = doPatch(patchState, project.documentSelfLink);
        assertNotNull(updatedProject.customProperties);
        assertEquals(3, updatedProject.customProperties.size());
        assertEquals(updatedValA, updatedProject.customProperties.get(CUSTOM_PROP_KEY_A));
        assertEquals(CUSTOM_PROP_VAL_B, updatedProject.customProperties.get(CUSTOM_PROP_KEY_B));
    }

    @Test
    public void testGetProjectByCustomPropertyValue() throws Throwable {
        final String searchProperty = "searchProperty";
        final String anotherProperty = "anotherProperty";
        final String searchValue = "searchValue";
        final String anotherValue = "anotherValue";
        final String targetProjectName = "target-project";

        // create some projects. Later we want to select only one of them (targetProject) based on
        // the searchValue of the searchProperty
        ProjectState targetProject = createProject(targetProjectName,
                createCustomPropertiesMap(searchProperty, searchValue));
        createProject("another-project-1", createCustomPropertiesMap(searchProperty, anotherValue));
        createProject("another-project-2", createCustomPropertiesMap(anotherProperty, searchValue));
        createProject("another-project-3",
                createCustomPropertiesMap(anotherProperty, anotherValue));

        // Build a ODATA query that selects only a single project which has the custom property
        // searchProperty set to searchValue
        String filterQuery = String
                .format("$filter=%s eq '%s'",
                        QuerySpecification.buildCompositeFieldName(
                                ProjectService.FIELD_NAME_CUSTOM_PROPERTIES, searchProperty),
                        searchValue);

        host.testStart(1);
        // Test simple filtered GET request to the factory
        URI uri = UriUtils.buildUri(host, ProjectFactoryService.SELF_LINK,
                filterQuery);
        Operation
                .createGet(UriUtils.buildExpandLinksQueryUri(uri))
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        // assert only one document was returned and assert this is the
                        // targetProject
                        ServiceDocumentQueryResult factoryResponse = o
                                .getBody(ServiceDocumentQueryResult.class);
                        try {
                            assertEquals(new Long(1), factoryResponse.documentCount);
                            assertNotNull(factoryResponse.documentLinks);
                            assertNotNull(factoryResponse.documents);
                            assertEquals(1, factoryResponse.documentLinks.size());
                            assertEquals(1, factoryResponse.documents.size());

                            assertEquals(targetProject.documentSelfLink,
                                    factoryResponse.documentLinks.iterator().next());

                            ProjectState selectedProject = Utils.fromJson(
                                    factoryResponse.documents.values().iterator().next(),
                                    ProjectState.class);
                            assertEquals(Utils.buildKind(ProjectState.class),
                                    selectedProject.documentKind);

                            assertEquals(targetProject.documentSelfLink,
                                    selectedProject.documentSelfLink);
                            assertNotNull(selectedProject.customProperties);
                            assertEquals(2, selectedProject.customProperties.size());
                            assertEquals(searchValue,
                                    selectedProject.customProperties.get(searchProperty));

                            host.completeIteration();
                        } catch (Throwable ex) {
                            host.failIteration(ex);
                        }
                    }
                }).sendWith(host);
        host.testWait();
    }

    private Map<String, String> createCustomPropertiesMap(String... keyValues) {
        assertNotNull(keyValues);
        assertTrue("keyValues must contain an even number of elements", keyValues.length % 2 == 0);

        HashMap<String, String> result = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }

        return result;
    }

}
