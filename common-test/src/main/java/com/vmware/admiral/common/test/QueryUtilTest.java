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

package com.vmware.admiral.common.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class QueryUtilTest {
    public static final String TENANTS_IDENTIFIER = "/tenants/";
    public static final String GROUP_IDENTIFIER = "/groups/";
    public static final String USER_IDENTIFIER = "/users/";

    private static final String TENANT_ID = TENANTS_IDENTIFIER + "tenantId";
    private static final String SUB_TENANT_ID = GROUP_IDENTIFIER + "subtenantId";
    private static final String USER_ID = USER_IDENTIFIER + "userId";

    @Test
    public void testAddTenantClauseWhenTenantLinksIsNull() {
        List<String> tenantLinks = null;

        Query query = QueryUtil.addTenantClause(tenantLinks);

        assertNotNull(query);
        assertEquals(query.occurance, Occurance.MUST_NOT_OCCUR);
        assertEquals(query.term.matchValue, UriUtils.URI_WILDCARD_CHAR);
        assertEquals(query.term.matchType, MatchType.WILDCARD);
    }

    // [/tenants/tenantId]
    @Test
    public void testAddTenantClauseWhenOnlyTenantIsSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID);
        Query query = QueryUtil.addTenantClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, TENANT_ID);
    }

    // [/tenants/tenantId, /groups/subtenantId]
    @Test
    public void testAddTenantClauseWhenTenantAndSubTenantAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, SUB_TENANT_ID);

        Query query = QueryUtil.addTenantClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(SUB_TENANT_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /users/subtenantId]
    @Test
    public void testAddTenantClauseWhenTenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, USER_ID);

        Query query = QueryUtil.addTenantClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query userQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /groups/subtenantId, /users/userId]
    @Test
    public void testAddTenantClauseWhenTenantAndSubtenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(
                TENANT_ID,
                SUB_TENANT_ID,
                USER_ID);
        Query query = QueryUtil.addTenantClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(SUB_TENANT_ID)).findFirst().get();
        Query userQuery = query.booleanClauses.stream().filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }
}
