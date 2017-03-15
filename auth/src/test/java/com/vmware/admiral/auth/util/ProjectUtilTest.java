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

import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

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
}
