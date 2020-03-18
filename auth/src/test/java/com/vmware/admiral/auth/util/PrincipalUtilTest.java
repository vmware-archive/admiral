/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.util;

import static com.vmware.admiral.auth.util.PrincipalUtil.copyPrincipalData;
import static com.vmware.admiral.auth.util.PrincipalUtil.decode;
import static com.vmware.admiral.auth.util.PrincipalUtil.encode;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromLocalPrincipalToPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromPrincipalToLocalPrincipal;
import static com.vmware.admiral.auth.util.PrincipalUtil.fromQueryResultToPrincipalList;
import static com.vmware.admiral.auth.util.PrincipalUtil.toNameAndDomain;
import static com.vmware.admiral.auth.util.PrincipalUtil.toPrincipalId;
import static com.vmware.admiral.auth.util.PrincipalUtil.toPrincipalName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.photon.controller.model.adapters.util.Pair;
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
        assertEquals(null, fromLocalPrincipalToPrincipal(null));

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
        assertEquals(null, fromPrincipalToLocalPrincipal(null));

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
        String name = toPrincipalName("First", "Last", "firstlast@test.local");
        assertEquals("First Last", name);

        name = toPrincipalName("First", "", "firstlast@test.local");
        assertEquals("First", name);

        name = toPrincipalName("First", null, "firstlast@test.local");
        assertEquals("First", name);

        name = toPrincipalName("", "Last", "firstlast@test.local");
        assertEquals("Last", name);

        name = toPrincipalName(null, "Last", "firstlast@test.local");
        assertEquals("Last", name);

        name = toPrincipalName("", "", "firstlast@test.local");
        assertEquals("firstlast@test.local", name);

        name = toPrincipalName(null, null, "firstlast@test.local");
        assertEquals("firstlast@test.local", name);

        name = toPrincipalName(null, null, "");
        assertEquals("", name);

        name = toPrincipalName(null, null, null);
        assertEquals(null, name);
    }

    @Test
    public void testEncodeDecode() {
        String fritzEmail = "fritz@admiral.com";
        String encodedEmail = encode(fritzEmail);
        assertNotEquals(fritzEmail, encodedEmail);
        String decodedEmail = decode(encodedEmail);
        assertEquals(fritzEmail, decodedEmail);
    }

    @Test
    public void testEncodeDecodeNoop() {
        assertEquals(null, encode(null));
        assertEquals("", encode(""));
        assertEquals(null, decode(null));
        assertEquals("", decode(""));
    }

    @Test(expected = RuntimeException.class)
    public void testDecodeException() {
        decode("1");
    }

    @Test
    public void testEncodeDecodeVca() {
        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.VCA_MODE_PROPERTY;
        config.value = Boolean.toString(true);
        ConfigurationUtil.initialize(config);

        String fritzEmail = "fritz@admiral.com";
        String encodedEmail = encode(fritzEmail);
        assertEquals(fritzEmail, encodedEmail);
        String decodedEmail = decode(encodedEmail);
        assertEquals(fritzEmail, decodedEmail);

        ConfigurationUtil.initialize((ConfigurationState[]) null);
    }

    @Test
    public void testToNameAndDomain() {
        Pair<String, String> pair = toNameAndDomain("username@domain");
        assertEquals("username", pair.left);
        assertEquals("domain", pair.right);

        pair = toNameAndDomain("user@name@domain");
        assertEquals("user@name", pair.left);
        assertEquals("domain", pair.right);

        pair = toNameAndDomain("super.user@domain.com");
        assertEquals("super.user", pair.left);
        assertEquals("domain.com", pair.right);

        pair = toNameAndDomain("super@user@domain.com");
        assertEquals("super@user", pair.left);
        assertEquals("domain.com", pair.right);

        pair = toNameAndDomain("domain\\username");
        assertEquals("username", pair.left);
        assertEquals("domain", pair.right);

        pair = toNameAndDomain("domain.com\\super.user");
        assertEquals("super.user", pair.left);
        assertEquals("domain.com", pair.right);

        // it could be a valid NETBIOS name and domain
        pair = toNameAndDomain("user\\name@domain\\com");
        assertEquals("name@domain\\com", pair.left); // name
        assertEquals("user", pair.right); // domain

        // it could be a valid UPN name and domain
        pair = toNameAndDomain("domain@com\\user@name");
        assertEquals("domain@com\\user", pair.left); // name
        assertEquals("name", pair.right); // domain

        // it could be a valid UPN and NETBIOS name and domain...
        // but UPC takes preference, sorry
        pair = toNameAndDomain("domain.com\\super@user");
        assertEquals("domain.com\\super", pair.left); // name
        assertEquals("user", pair.right); // domain
    }

    @Test
    public void testInvalidToNameAndDomain() {
        try {
            toNameAndDomain("usernamedomain");
            fail("It should have failed!");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid principalId format:"));
        }

        try {
            toNameAndDomain("username@domain\\com");
            fail("It should have failed!");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid principalId format:"));
        }

        try {
            toNameAndDomain("domain@com\\username");
            fail("It should have failed!");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid principalId format:"));
        }
    }

    @Test
    public void testToPrincipalId() {
        String principalId = toPrincipalId("name", null);
        assertEquals("name", principalId);

        principalId = toPrincipalId("name", "domain");
        assertEquals("name@domain", principalId);
    }

    @Test
    public void testInvalidToPrincipalId() {
        try {
            toPrincipalId(null, null);
            fail("It should have failed!");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid principal name:"));
        }

        try {
            toPrincipalId("", null);
            fail("It should have failed!");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid principal name:"));
        }
    }

    @Test
    public void testCopyPrincipalData() {
        assertEquals(null, copyPrincipalData(null, new Principal()));

        Principal principal = new Principal();
        assertEquals(principal, copyPrincipalData(principal, null));

        principal = copyPrincipalData(testPrincipal, principal);
        assertEquals(testPrincipal.id, principal.id);
        assertEquals(testPrincipal.email, principal.email);
        assertEquals(testPrincipal.type, principal.type);
        assertEquals(testPrincipal.name, principal.name);
        assertEquals(testPrincipal.password, principal.password);
    }
}
