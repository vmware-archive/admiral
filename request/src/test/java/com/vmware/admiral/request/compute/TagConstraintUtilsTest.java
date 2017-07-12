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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.services.common.QueryTask;

public class TagConstraintUtilsTest extends RequestBaseTest {

    @Test
    public void testSatisfiedHardRequirement() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", true, false)));
        String tag = createTag("cap", "pci", networkDescription.tenantLinks);
        SubnetState subnet = TestRequestStateFactory.createSubnetState("my-subnet");
        subnet.tagLinks.add(tag);

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet).stream(),
                s -> s.tagLinks, null)
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(1, filteredSubnets.size());
        assertEquals(subnet, filteredSubnets.iterator().next());
    }

    @Test
    public void testSatisfiedAntiRequirement() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", true, true)));
        SubnetState subnet = TestRequestStateFactory.createSubnetState("my-subnet");

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet).stream(),
                s -> s.tagLinks, null)
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(1, filteredSubnets.size());
        assertEquals(subnet, filteredSubnets.iterator().next());
    }

    @Test
    public void testUnSatisfiedHardRequirement() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", true, false)));
        String tag = createTag("cap", "noPci", networkDescription.tenantLinks);
        SubnetState subnet = TestRequestStateFactory.createSubnetState("my-subnet");
        subnet.tagLinks.add(tag);

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet).stream(),
                s -> s.tagLinks, null)
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(0, filteredSubnets.size());
    }

    @Test
    public void testUnSatisfiedSoftRequirement() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", false, false)));
        String tag = createTag("cap", "noPci", networkDescription.tenantLinks);
        SubnetState subnet = TestRequestStateFactory.createSubnetState("my-subnet");
        subnet.tagLinks.add(tag);

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet).stream(),
                s -> s.tagLinks, null)
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(1, filteredSubnets.size());
        assertEquals(subnet, filteredSubnets.iterator().next());
    }

    @Test
    public void testSortBySoftRequirement() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", false, false)));
        String tag = createTag("cap", "pci", networkDescription.tenantLinks);
        SubnetState subnet1 = TestRequestStateFactory.createSubnetState("my-subnet1");
        SubnetState subnet2 = TestRequestStateFactory.createSubnetState("my-subnet2");
        subnet2.tagLinks.add(tag);

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet1, subnet2).stream(),
                s -> s.tagLinks, null)
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(2, filteredSubnets.size());
        assertEquals(subnet2, filteredSubnets.iterator().next());
    }

    @Test
    public void testSecondarySort() throws Throwable {
        ComputeNetworkDescription networkDescription = TestRequestStateFactory
                .createComputeNetworkDescription("my-net");
        networkDescription.constraints = createConstraints(
                Arrays.asList(createCondition("cap", "pci", false, false)));
        String tag = createTag("cap", "pci", networkDescription.tenantLinks);
        SubnetState subnet1 = TestRequestStateFactory.createSubnetState("my-subnet1");
        subnet1.tagLinks.add(tag);
        SubnetState subnet2 = TestRequestStateFactory.createSubnetState("my-subnet2");
        subnet2.tagLinks.add(tag);

        List<SubnetState> filteredSubnets = TagConstraintUtils.filterByConstraints(
                TagConstraintUtils.extractPlacementTagConditions(networkDescription.constraints,
                        networkDescription.tenantLinks),
                Arrays.asList(subnet2, subnet1).stream(),
                s -> s.tagLinks,
                (s1, s2) -> s1.documentSelfLink.compareTo(s2.documentSelfLink))
                .collect(Collectors.toList());

        assertNotNull(filteredSubnets);
        assertEquals(2, filteredSubnets.size());
        assertEquals(subnet1, filteredSubnets.iterator().next());
    }

    @Test
    public void testMemoize() throws Throwable {

        AtomicInteger numberOfCalls = new AtomicInteger(0);

        Function<String, Integer> strLength = str -> {
            numberOfCalls.incrementAndGet();
            return str.length();
        };

        strLength.apply("test str");
        strLength.apply("test str");
        Assert.assertEquals("Number of calls mismatch before memoize", 2, numberOfCalls.get());

        strLength = TagConstraintUtils.memoize(strLength);

        strLength.apply("test str");
        strLength.apply("test str");
        Assert.assertEquals("Number of calls mismatch after memoize", 3, numberOfCalls.get());

        strLength.apply("different test str");
        strLength.apply("different test str");
        Assert.assertEquals("Number of calls mismatch after memoize", 4, numberOfCalls.get());

        Assert.assertSame("Memoizing a function should be idempotent", strLength, TagConstraintUtils.memoize(strLength));
    }


    private static Map<String, Constraint> createConstraints(List<Condition> conditions) {
        Constraint constraint = new Constraint();
        constraint.conditions = conditions;
        Map<String, Constraint> constraints = new HashMap<>();
        constraints.put(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
        return constraints;
    }

    private static Condition createCondition(String tagKey, String tagValue, boolean isHard,
            boolean isAnti) {
        return Constraint.Condition.forTag(tagKey, tagValue, isHard ?
                        Constraint.Condition.Enforcement.HARD :
                        Constraint.Condition.Enforcement.SOFT,
                isAnti ?
                        QueryTask.Query.Occurance.MUST_NOT_OCCUR :
                        QueryTask.Query.Occurance.MUST_OCCUR);
    }

    private String createTag(String key, String value, List<String> tenantLinks)
            throws Throwable {
        TagState tag = new TagState();
        tag.key = key;
        tag.value = value;
        tag.tenantLinks = tenantLinks;
        return doPost(tag, TagService.FACTORY_LINK).documentSelfLink;
    }
}
