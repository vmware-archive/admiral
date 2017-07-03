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

package com.vmware.admiral.auth.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class ProjectUtilTest {

    @Test
    public void testCreateQueryTaskForProjectAssociatedWithPlacement() {
        ProjectState project = new ProjectState();
        project.documentSelfLink = "test-self-link";

        Query query = new Query();
        query.occurance = Occurance.MUST_OCCUR;

        // test with query not null
        QueryTask task = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(project, query);
        assertNotNull(task);
        assertEquals(query.occurance, task.querySpec.query.occurance);

        // test with resource not null and query null
        task = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(project, null);
        assertNotNull(task);
        assertEquals(2, task.querySpec.query.booleanClauses.size());
        assertEquals(ServiceDocument.FIELD_NAME_KIND, task.querySpec.query.booleanClauses.get(0).term.propertyName);
        assertEquals(project.documentSelfLink, task.querySpec.query.booleanClauses.get(1).term.matchValue);

        // test with resource null and query null
        task = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(null, null);
        assertNull(task);
    }

    @Test
    public void testBuildQueryProjectsFromGroups() {
        List<String> groupLinks = Arrays.asList("/groups/a", "/groups/b", "/groups/c", "/groups/d");
        Query query = ProjectUtil.buildQueryProjectsFromGroups(groupLinks);

        assertNotNull(query.booleanClauses);
        // kind and group clauses
        assertEquals(2, query.booleanClauses.size());

        // verify kind clause
        Query kindClause = query.booleanClauses.get(0);
        assertEquals(Occurance.MUST_OCCUR, kindClause.occurance);
        assertEquals(ServiceDocument.FIELD_NAME_KIND, kindClause.term.propertyName);
        assertEquals(Utils.buildKind(ProjectState.class), kindClause.term.matchValue);

        // verify group clauses
        Query groupClause = query.booleanClauses.get(1);
        assertEquals(Occurance.MUST_OCCUR, groupClause.occurance);
        assertNotNull(groupClause.booleanClauses);
        // one for admins, one for members
        assertEquals(3, groupClause.booleanClauses.size());
        for (Query clause : groupClause.booleanClauses) {
            assertEquals(Occurance.SHOULD_OCCUR, clause.occurance);
            assertNotNull(clause.booleanClauses);
            assertEquals(groupLinks.size(), clause.booleanClauses.size());
            for (Query subclause: clause.booleanClauses) {
                assertTrue(Arrays
                        .asList(QuerySpecification.buildCollectionItemName(
                                ProjectState.FIELD_NAME_ADMINISTRATORS_USER_GROUP_LINKS),
                                QuerySpecification.buildCollectionItemName(
                                        ProjectState.FIELD_NAME_MEMBERS_USER_GROUP_LINKS),
                                QuerySpecification.buildCollectionItemName(
                                        ProjectState.FIELD_NAME_VIEWERS_USER_GROUP_LINKS))
                        .contains(subclause.term.propertyName));
                assertTrue(groupLinks.contains(subclause.term.matchValue));
            }
        }
    }

    @Test
    public void testGetHarborId() {
        ProjectState state = new ProjectState();

        state.customProperties = null;
        assertNull(ProjectUtil.getProjectIndex(state));

        state.customProperties = new HashMap<>();
        assertNull(ProjectUtil.getProjectIndex(state));

        String projectId =  "" + new Random().nextInt(1000);
        state.customProperties.put(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX, projectId);
        assertEquals(projectId, ProjectUtil.getProjectIndex(state));
    }
}
