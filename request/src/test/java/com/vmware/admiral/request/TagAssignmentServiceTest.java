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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.service.common.TagAssignmentService;
import com.vmware.admiral.service.common.TagAssignmentService.KeyValue;
import com.vmware.admiral.service.common.TagAssignmentService.TagAssignmentRequest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link TagAssignmentService} class.
 */
public class TagAssignmentServiceTest extends RequestBaseTest {

    @Test
    public void testAssignmentAndUnassignment() throws Throwable {
        ComputeState compute = createCompute();

        // addition
        updateTags(compute.documentSelfLink,
                Arrays.asList("prop", "key1:value2", "key2:value2"),
                Arrays.asList(),
                Arrays.asList("prop", "key1:value2", "key2:value2"));

        // addition + removal
        updateTags(compute.documentSelfLink,
                Arrays.asList("location:somewhere"),
                Arrays.asList("key2:value2"),
                Arrays.asList("prop", "key1:value2", "location:somewhere"));

        // no change
        updateTags(compute.documentSelfLink,
                Arrays.asList("location:somewhere"),
                Arrays.asList("key2:value2"),
                Arrays.asList("prop", "key1:value2", "location:somewhere"));

        // empty request
        updateTags(compute.documentSelfLink,
                Arrays.asList(),
                Arrays.asList(),
                Arrays.asList("prop", "key1:value2", "location:somewhere"));

        // null addition
        updateTags(compute.documentSelfLink,
                null,
                Arrays.asList(),
                Arrays.asList("prop", "key1:value2", "location:somewhere"));

        // null removal
        updateTags(compute.documentSelfLink,
                Arrays.asList(),
                null,
                Arrays.asList("prop", "key1:value2", "location:somewhere"));

        // removal
        updateTags(compute.documentSelfLink,
                Arrays.asList(),
                Arrays.asList("key1:value2"),
                Arrays.asList("prop", "location:somewhere"));

        // removal of all
        updateTags(compute.documentSelfLink,
                null,
                Arrays.asList("prop", "location:somewhere"),
                Arrays.asList());
    }

    @Test
    public void testNoLinkRequest() throws Throwable {
        updateTags(null, null, null, Arrays.asList());
        updateTags(null, Arrays.asList("key2:value2"), null, Arrays.asList("key2:value2"));
    }

    @Test(expected = ServiceNotFoundException.class)
    public void testInvalidLinkRequest() throws Throwable {
        TagAssignmentRequest request = new TagAssignmentRequest();
        request.resourceLink = "invalid-link";
        doOperation(request,
                UriUtils.buildUri(this.host, TagAssignmentService.SELF_LINK),
                TagAssignmentRequest.class, false, Action.POST);
    }

    private ComputeState createCompute() throws Throwable {
        ComputeState compute = new ComputeState();
        compute.name = UUID.randomUUID().toString();
        compute.descriptionLink = "/link/desc";
        return doPost(compute, ComputeService.FACTORY_LINK);
    }

    private void updateTags(String link, List<String> tagsToAdd, List<String> tagsToRemove,
            List<String> expectedTags) throws Throwable {
        TagAssignmentRequest request = new TagAssignmentRequest();
        request.resourceLink = link;
        request.tagsToAssign = listOfStringToKv(tagsToAdd);
        request.tagsToUnassign = listOfStringToKv(tagsToRemove);
        TagAssignmentRequest response = doOperation(request,
                UriUtils.buildUri(this.host, TagAssignmentService.SELF_LINK),
                TagAssignmentRequest.class, false, Action.POST);

        assertNotNull(response.tagLinks);
        List<String> remainingTags = new ArrayList<>(expectedTags);
        for (String tagLink : response.tagLinks) {
            TagState tag = getDocument(TagState.class, tagLink);
            String tagAsString = tag.key + (tag.value != "" ? (":" + tag.value) : "");
            assertTrue("Unexpected tag returned: " + tagAsString, remainingTags.remove(tagAsString));
        }
        assertEquals("Expected tags not found: " + remainingTags, 0, remainingTags.size());
    }

    private List<KeyValue> listOfStringToKv(List<String> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream().map(this::stringToKv).collect(Collectors.toList());
    }

    private KeyValue stringToKv(String tagString) {
        String[] parts = tagString.split(":");
        KeyValue kv = new KeyValue(parts[0], parts.length > 1 ? parts[1] : "");
        return kv;
    }
}
