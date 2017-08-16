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

import static com.vmware.admiral.auth.util.PrincipalUtil.fromLocalPrincipalToPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromPrincipalToLocalPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromQueryResultToPrincipalList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.local.LocalPrincipalFactoryService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

public class PrincipalUtilTest {
    private LocalPrincipalState testLocalPrincipal;

    private Principal testPrincipal;

    @Before
    public void setup() {
        testLocalPrincipal = new LocalPrincipalState();
        testLocalPrincipal.id = "test@admiral.com";
        testLocalPrincipal.email = testLocalPrincipal.id;
        testLocalPrincipal.name = "Test User";
        testLocalPrincipal.type = LocalPrincipalType.USER;
        testLocalPrincipal.documentSelfLink = "testSelfLink";

        testPrincipal = new Principal();
        testPrincipal.id = "test@admiral.com";
        testPrincipal.email = testLocalPrincipal.id;
        testPrincipal.name = "Test User";
        testPrincipal.type = PrincipalType.USER;
    }

    @Test
    public void testFromLocalPrincipalToPrincipalOfTypeUser() {
        Principal principal = fromLocalPrincipalToPrincipal(testLocalPrincipal);
        assertEquals(testLocalPrincipal.id, principal.id);
        assertEquals(testLocalPrincipal.email, principal.email);
        assertEquals(testLocalPrincipal.name, principal.name);
        assertEquals(PrincipalType.USER, principal.type);
    }

    @Test
    public void testFromLocalPrincipalToPrincipalOfTypeGroup() {
        LocalPrincipalState localPrincipal = new LocalPrincipalState();
        localPrincipal.id = "superadmins";
        localPrincipal.name = "Super Admins";
        localPrincipal.type = LocalPrincipalType.GROUP;
        localPrincipal.groupMembersLinks = new ArrayList<>();
        localPrincipal.groupMembersLinks.add(UriUtils
                .buildUriPath(LocalPrincipalFactoryService.SELF_LINK, "connie@admiral.com"));
        localPrincipal.groupMembersLinks.add(
                UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK, "fritz@admiral.com"));

        Principal principal = fromLocalPrincipalToPrincipal(localPrincipal);
        assertEquals(localPrincipal.id, principal.id);
        assertEquals(localPrincipal.name, principal.name);
        assertEquals(PrincipalType.GROUP, principal.type);
    }

    @Test
    public void testFromQueryResultToPrincipalList() {
        ServiceDocumentQueryResult queryResult = new ServiceDocumentQueryResult();
        queryResult.documents = new HashMap<>();
        queryResult.documents.put(testLocalPrincipal.documentSelfLink, testLocalPrincipal);
        queryResult.documentLinks.add(testLocalPrincipal.documentSelfLink);

        List<Principal> principals = fromQueryResultToPrincipalList(queryResult);

        assertNotNull(principals);
        Principal principal = principals.get(0);
        assertEquals(testLocalPrincipal.id, principal.id);
        assertEquals(testLocalPrincipal.email, principal.email);
        assertEquals(testLocalPrincipal.name, principal.name);
        assertEquals(PrincipalType.USER, principal.type);
    }

    @Test
    public void testFromPrincipalToLocalPrincipalOfTypeUser() {
        LocalPrincipalState localPrincipal = fromPrincipalToLocalPrincipal(testPrincipal);
        assertEquals(testPrincipal.id, localPrincipal.id);
        assertEquals(testPrincipal.email, localPrincipal.email);
        assertEquals(testPrincipal.name, localPrincipal.name);
        assertEquals(LocalPrincipalType.USER, localPrincipal.type);
    }

    @Test
    public void testFromPrincipalToLocalPrincipalOfTypeGroup() {
        Principal principal = new Principal();
        principal.id = "superadmins";
        principal.name = "Super Admins";
        principal.type = PrincipalType.GROUP;

        LocalPrincipalState localPrincipal = fromPrincipalToLocalPrincipal(principal);
        assertEquals(principal.id, localPrincipal.id);
        assertEquals(principal.name, localPrincipal.name);
        assertEquals(LocalPrincipalType.GROUP, localPrincipal.type);
    }

    @Test
    public void testGetPrincipalName() {
        String name = PrincipalUtil.toPrincipalName("First", "Last", "firstlast@test.local");
        assertEquals("First Last", name);

        name = PrincipalUtil.toPrincipalName("First", "", "firstlast@test.local");
        assertEquals("First", name);

        name = PrincipalUtil.toPrincipalName("First", null, "firstlast@test.local");
        assertEquals("First", name);

        name = PrincipalUtil.toPrincipalName("", "Last", "firstlast@test.local");
        assertEquals("Last", name);

        name = PrincipalUtil.toPrincipalName(null, "Last", "firstlast@test.local");
        assertEquals("Last", name);

        name = PrincipalUtil.toPrincipalName("", "", "firstlast@test.local");
        assertEquals("firstlast@test.local", name);

        name = PrincipalUtil.toPrincipalName(null, null, "firstlast@test.local");
        assertEquals("firstlast@test.local", name);

        name = PrincipalUtil.toPrincipalName(null, null, "");
        assertEquals("", name);

        name = PrincipalUtil.toPrincipalName(null, null, null);
        assertEquals(null, name);
    }
}
