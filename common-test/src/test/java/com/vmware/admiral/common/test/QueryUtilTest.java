/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class QueryUtilTest {
    private static final String TENANT_ID = MultiTenantDocument.TENANTS_PREFIX + "/tenantId";
    private static final String GROUP_ID = TENANT_ID + "/" + MultiTenantDocument.GROUP_IDENTIFIER
            + "/groupId";
    private static final String USER_ID = MultiTenantDocument.USERS_PREFIX + "/userId";
    private static final String PROJECT_ID = MultiTenantDocument.PROJECTS_IDENTIFIER + "/projectId";

    private static String T1 = "t1";
    private static String T2 = "t2";
    private static String T1_G1 = "t1g1";
    private static String T1_G2 = "t1g2";
    private static String T2_G1 = "t2g1";
    private static String U1 = "john.doe@mydomain.org";
    private static String U2 = "jane.roe@mydomain.org";
    private static String TLINK_T1 = QueryUtil.TENANT_IDENTIFIER + T1;
    private static String TLINK_T1_G1 = TLINK_T1 + QueryUtil.GROUP_IDENTIFIER + T1_G1;
    private static String TLINK_T1_G2 = TLINK_T1 + QueryUtil.GROUP_IDENTIFIER + T1_G2;
    private static String TLINK_T2 = QueryUtil.TENANT_IDENTIFIER + T2;
    private static String TLINK_T2_G1 = TLINK_T2 + QueryUtil.GROUP_IDENTIFIER + T2_G1;
    private static String TLINK_U1 = QueryUtil.USER_IDENTIFIER + U1;
    private static String TLINK_U2 = QueryUtil.USER_IDENTIFIER + U2;

    @Test
    public void testAddTenantGroupAndUserClauseWhenTenantLinksIsNull() {
        List<String> tenantLinks = null;

        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        assertNotNull(query);
        assertEquals(query.occurance, Occurance.MUST_NOT_OCCUR);
        assertEquals(query.term.matchValue, UriUtils.URI_WILDCARD_CHAR);
        assertEquals(query.term.matchType, MatchType.WILDCARD);
    }

    // [/tenants/tenantId]
    @Test
    public void testAddTenantGroupAndUserClauseWhenOnlyTenantIsSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID);
        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, TENANT_ID);
    }

    // [/tenants/tenantId, /groups/subtenantId]
    @Test
    public void testAddTenantGroupAndUserClauseWhenTenantAndSubTenantAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID);

        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(GROUP_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /users/userId]
    @Test
    public void testAddTenantGroupAndUserClauseWhenTenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, USER_ID);

        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query userQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /groups/subtenantId, /users/userId]
    @Test
    public void testAddTenantGroupAndUserClauseWhenTenantAndSubtenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID, USER_ID);
        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(GROUP_ID)).findFirst().get();
        Query userQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }

    @Test
    public void testAddTenantAndGroupClauseWhenTenantLinksIsNull() {
        List<String> tenantLinks = null;

        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);

        assertNotNull(query);
        assertEquals(query.occurance, Occurance.MUST_NOT_OCCUR);
        assertEquals(query.term.matchValue, UriUtils.URI_WILDCARD_CHAR);
        assertEquals(query.term.matchType, MatchType.WILDCARD);
    }

    // [group-tag]
    @Test
    public void testAddGroupTagWhenOnlyTenantIsSet() {
        String groupTag = "group-tag1";
        List<String> tenantLinks = Arrays.asList(groupTag);
        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, groupTag);
    }

    // [/tenants/tenantId]
    @Test
    public void testAddTenantAndGroupClauseWhenOnlyTenantIsSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID);
        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, TENANT_ID);
    }

    // [/tenants/tenantId, /tenants/tenantId/groups/subtenantId]
    @Test
    public void testAddTenantAndGroupClauseWhenTenantAndSubTenantAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID);

        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(GROUP_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /users/userId]
    @Test
    public void testAddTenantAndGroupClauseWhenTenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, USER_ID);

        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);
        assertNull("assumtion failed for query being single", query.booleanClauses);
        assertTrue(query.term.matchValue.contains(TENANT_ID));
    }

    // [/tenants/tenantId, /tenants/tenantId/groups/subtenantId, /users/userId]
    @Test
    public void testAddTenantAndGroupClauseWhenTenantAndSubtenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID, USER_ID);
        Query query = QueryUtil.addTenantAndGroupClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query subTenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(GROUP_ID)).findFirst().get();
        Optional<Query> userOptional = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(USER_ID)).findFirst();

        assertNotNull(tenantQuery);
        assertNotNull(subTenantQuery);
        assertEquals(Optional.empty(), userOptional);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(subTenantQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId]
    @Test
    public void testAddTenantAndUserClauseWhenOnlyTenantIsSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID);
        Query query = QueryUtil.addTenantAndUserClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, TENANT_ID);
    }

    // [/tenants/tenantId, /users/userId]
    @Test
    public void testAddTenantAndUserClauseWhenTenantAndSubTenantAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, USER_ID);

        Query query = QueryUtil.addTenantAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query userQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/tenants/tenantId, /tenants/tenantId/groups/subtenantId]
    @Test
    public void testAddTenantAndUserClauseWhenTenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID);

        Query query = QueryUtil.addTenantAndUserClause(tenantLinks);
        assertNull("assumtion failed for query being single", query.booleanClauses);
        assertTrue(query.term.matchValue.contains(TENANT_ID));
    }

    // [/tenants/tenantId, /tenants/tenantId/groups/subtenantId, /users/userId]
    @Test
    public void testAddTenantAndUserClauseWhenTenantAndSubtenantAndUserAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, GROUP_ID, USER_ID);
        Query query = QueryUtil.addTenantAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Optional<Query> subTenantOptional = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(GROUP_ID)).findFirst();
        Query userQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(USER_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertEquals(Optional.empty(), subTenantOptional);
        assertNotNull(userQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(userQuery.occurance, Occurance.MUST_OCCUR);
    }

    // [/projects/projectId]
    @Test
    public void testAddProjectLinkClauseWhenOnlyProjectIsSet() {
        List<String> tenantLinks = Arrays.asList(PROJECT_ID);
        Query query = QueryUtil.addTenantGroupAndUserClause(tenantLinks);

        assertNotNull(query);

        assertEquals(query.occurance, Occurance.MUST_OCCUR);
        assertEquals(query.term.matchValue, PROJECT_ID);
    }

    // [/tenants/tenantId, /projects/projectId]
    @Test
    public void testAddTenantAndProjectClauseWhenTenantAndProjectAreSet() {
        List<String> tenantLinks = Arrays.asList(TENANT_ID, PROJECT_ID);

        Query query = QueryUtil.addTenantAndUserClause(tenantLinks);

        Query tenantQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(TENANT_ID)).findFirst().get();
        Query projectQuery = query.booleanClauses.stream()
                .filter(l -> l.term.matchValue.contains(PROJECT_ID)).findFirst().get();

        assertNotNull(tenantQuery);
        assertNotNull(projectQuery);

        assertEquals(tenantQuery.occurance, Occurance.MUST_OCCUR);
        assertEquals(projectQuery.occurance, Occurance.MUST_OCCUR);
    }

    @Test
    public void testGetTenants() throws Exception {
        List<String> tenants = QueryUtil.getTenants(getTenantLinks());
        Assert.assertNotNull(tenants);
        Assert.assertEquals(new HashSet<>(Arrays.asList(T1, T2)),
                new HashSet<>(tenants)
        );
    }

    @Test
    public void testGetTenantsNull() throws Exception {
        List<String> tenants = QueryUtil.getTenants(null);
        Assert.assertNull(tenants);
    }

    @Test
    public void testGetGroups() throws Exception {
        List<String> tenants = QueryUtil.getGroups(getTenantLinks());
        Assert.assertNotNull(tenants);
        Assert.assertEquals(new HashSet<>(Arrays.asList(
                T1 + QueryUtil.GROUP_IDENTIFIER + T1_G1,
                T1 + QueryUtil.GROUP_IDENTIFIER + T1_G2,
                T2 + QueryUtil.GROUP_IDENTIFIER + T2_G1)),
                new HashSet<>(tenants)
        );
    }

    @Test
    public void testGetGroupsNull() throws Exception {
        List<String> tenants = QueryUtil.getGroups(null);
        Assert.assertNull(tenants);
    }

    @Test
    public void testGetUsers() throws Exception {
        List<String> tenants = QueryUtil.getUsers(getTenantLinks());
        Assert.assertNotNull(tenants);
        Assert.assertEquals(new HashSet<>(Arrays.asList(U1, U2)),
                new HashSet<>(tenants)
        );
    }

    @Test
    public void testGetUsersNull() throws Exception {
        List<String> tenants = QueryUtil.getUsers(null);
        Assert.assertNull(tenants);
    }

    private List<String> getTenantLinks() {
        return Arrays.asList(
                TLINK_T1,
                TLINK_T2,
                TLINK_T1_G1,
                TLINK_T1_G2,
                TLINK_T2_G1,
                TLINK_U1,
                TLINK_U2
        );
    }
}
