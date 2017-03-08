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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.ODataQueryVisitor.BinaryVerb;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link ComputeSearchService} class.
 */
public class ComputeSearchServiceTest extends ComputeBaseTest {

    private TagState createTag(String key, String value) throws Throwable {
        TagState tagState = new TagState();
        tagState.key = key;
        tagState.value = value;
        return doPost(tagState, TagService.FACTORY_LINK);
    }

    private ComputeState createCompute(String name, ComputeType type, String endpointLink, String... tagLinks)
            throws Throwable {
        ComputeDescription computeDescription = new ComputeDescriptionService.ComputeDescription();
        ArrayList<String> children = new ArrayList<>();
        children.add(type.toString());
        computeDescription.supportedChildren = children;
        computeDescription.bootAdapterReference = new URI("http://bootAdapterReference");
        computeDescription.powerAdapterReference = new URI("http://powerAdapterReference");
        computeDescription.instanceAdapterReference = new URI("http://instanceAdapterReference");
        computeDescription.healthAdapterReference = new URI("http://healthAdapterReference");
        computeDescription.enumerationAdapterReference = new URI("http://enumerationAdapterReference");
        computeDescription.dataStoreId = null;
        computeDescription.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        computeDescription.cpuMhzPerCore = 1000;
        computeDescription.cpuCount = 2;
        computeDescription.gpuCount = 1;
        computeDescription.totalMemoryBytes = Integer.MAX_VALUE;
        computeDescription.id = UUID.randomUUID().toString();
        computeDescription.name = "friendly-name";
        computeDescription.regionId = "provider-specific-regions";
        computeDescription.zoneId = "provider-specific-zone";
        computeDescription = doPost(computeDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeStateWithDescription computeState = new ComputeService.ComputeStateWithDescription();
        computeState.name = name;
        computeState.type = type;
        computeState.id = UUID.randomUUID().toString();
        computeState.description = computeDescription;
        computeState.descriptionLink = computeDescription.documentSelfLink;
        computeState.resourcePoolLink = null;
        computeState.address = "10.0.0.1";
        computeState.primaryMAC = "01:23:45:67:89:ab";
        computeState.powerState = ComputeService.PowerState.ON;
        computeState.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");
        computeState.diskLinks = new ArrayList<>();
        computeState.diskLinks.add("http://disk");
        computeState.networkInterfaceLinks = new ArrayList<>();
        computeState.networkInterfaceLinks.add("http://network");
        computeState.customProperties = new HashMap<>();
        computeState.endpointLink = endpointLink;
        computeState.tagLinks = new HashSet<String>(Arrays.asList(tagLinks));
        return doPost(computeState, ComputeService.FACTORY_LINK);
    }

    private String createQuery(ComputeType type, BinaryVerb operator, String directQuery, String endpointQuery,
            String tagQuery) {
        List<String> filter = new ArrayList<>();
        if (ComputeType.VM_HOST.equals(type) || ComputeType.ZONE.equals(type)) {
            filter.add("(type eq 'VM_HOST' or type eq 'ZONE')");
        } else if (ComputeType.VM_GUEST.equals(type)) {
            filter.add("type eq 'VM_GUEST'");
        }
        if (directQuery != null) {
            filter.add("(" + directQuery + ")");
        }

        List<String> params = new ArrayList<>();
        params.add("$filter=" + filter.stream().reduce((p1, p2) -> p1 + " and " + p2).orElse(""));

        if (operator != null) {
            params.add("operator=" + operator.toString().toLowerCase());
        }
        if (endpointQuery != null) {
            params.add("endpoint=" + endpointQuery);
        }
        if (tagQuery != null) {
            params.add("tag=" + tagQuery);
        }
        return params.stream().reduce((p1, p2) -> p1 + "&" + p2).orElse("");
    }

    @Test
    public void test() throws Throwable {
        waitForServiceAvailability(ComputeSearchService.SELF_LINK);

        EndpointState endpoint1 = doPost(createEndpoint("endpoint1"), EndpointService.FACTORY_LINK);
        EndpointState endpoint2 = doPost(createEndpoint("endpoint2"), EndpointService.FACTORY_LINK);

        TagState tag1 = createTag("category", "1");
        TagState tag2 = createTag("category", "2");

        ComputeState guest1 = createCompute("guest1", ComputeType.VM_GUEST, endpoint1.documentSelfLink);
        ComputeState guest2 = createCompute("guest2", ComputeType.VM_GUEST, endpoint1.documentSelfLink,
                tag1.documentSelfLink);
        ComputeState guest3 = createCompute("guest3", ComputeType.VM_GUEST, endpoint2.documentSelfLink,
                tag1.documentSelfLink, tag2.documentSelfLink);

        ComputeState host1 = createCompute("host1", ComputeType.VM_HOST, endpoint1.documentSelfLink);
        ComputeState host2 = createCompute("host2", ComputeType.VM_HOST, endpoint2.documentSelfLink,
                tag1.documentSelfLink);
        ComputeState host3 = createCompute("host3", ComputeType.VM_HOST, endpoint2.documentSelfLink,
                tag1.documentSelfLink, tag2.documentSelfLink);

        ComputeState zone1 = createCompute("zone1", ComputeType.VM_HOST, endpoint1.documentSelfLink);

        URI uri;
        ServiceDocumentQueryResult result;

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK,
                createQuery(ComputeType.VM_GUEST, BinaryVerb.AND, null, null, null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(3, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                "ALL_FIELDS eq '*guest1*'", "ALL_FIELDS eq '*endpoint1*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(guest1.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                "ALL_FIELDS eq '*guest*'", "ALL_FIELDS eq '*endpoint2*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(guest3.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK,
                createQuery(ComputeType.VM_GUEST, BinaryVerb.AND, null, "ALL_FIELDS eq '*endpoint*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(3, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                "ALL_FIELDS eq '*guest*'", "ALL_FIELDS eq '*endpoint3*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(0, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.OR,
                "ALL_FIELDS eq '*guest2*'", "ALL_FIELDS eq '*endpoint3*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(guest2.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK,
                createQuery(ComputeType.VM_GUEST, BinaryVerb.AND, null, null, "key eq '*category*'"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(2, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                null, "ALL_FIELDS eq '*endpoint2*'", "key eq 'category' and value eq '2*'"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(guest3.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.OR,
                null, "ALL_FIELDS eq '*endpoint3*'", "key eq '*category*'"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(2, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                "ALL_FIELDS eq '*guest*'", "ALL_FIELDS eq '*endpoint*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(3, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_GUEST, BinaryVerb.AND,
                "ALL_FIELDS eq '*guest*'", "ALL_FIELDS eq '*endpoint*'", "(key eq 'category' and value eq '1*') or (key eq 'category' and value eq '2*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(2, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK,
                createQuery(ComputeType.VM_GUEST, BinaryVerb.AND, null, "ALL_FIELDS eq '*endpoint1*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(2, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK,
                createQuery(ComputeType.VM_GUEST, BinaryVerb.AND, null, null, "key eq '*category*'"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(2, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.AND,
                "name eq '*host1*'", "ALL_FIELDS eq '*endpoint1*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(host1.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.AND,
                "name eq '*o*'", "ALL_FIELDS eq '*endpoint1*'", "(key eq 'category' and value eq '1*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(0, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.AND,
                "name eq '*host2*'", "ALL_FIELDS eq '*endpoint*'", "(key eq 'category' and value eq '1*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(host2.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.AND,
                "ALL_FIELDS eq '*o*'", "ALL_FIELDS eq '*endpoint2*'", "(key eq 'category' and value eq '2*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(host3.documentSelfLink, result.documentLinks.get(0));

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.AND,
                "ALL_FIELDS eq '*o*'", "ALL_FIELDS eq '*endpoint3*'", "(key eq '3*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(0, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.VM_HOST, BinaryVerb.OR,
                "ALL_FIELDS eq '*o*'", "ALL_FIELDS eq '*endpoint3*'", "(key eq '3*')"));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(4, result.documentLinks.size());

        uri = UriUtils.buildUri(host, ComputeSearchService.SELF_LINK, createQuery(ComputeType.ZONE, BinaryVerb.AND,
                "ALL_FIELDS eq '*zone*'", "ALL_FIELDS eq '*endpoint*'", null));
        result = host.waitForResponse(Operation.createGet(uri)).getBody(ServiceDocumentQueryResult.class);
        assertEquals(1, result.documentLinks.size());
        assertEquals(zone1.documentSelfLink, result.documentLinks.get(0));
    }
}
