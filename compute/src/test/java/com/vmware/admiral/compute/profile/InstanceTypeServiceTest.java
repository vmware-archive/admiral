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

package com.vmware.admiral.compute.profile;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.profile.InstanceTypeService.InstanceTypeFactoryService;
import com.vmware.admiral.compute.profile.InstanceTypeService.InstanceTypeState;
import com.vmware.admiral.compute.profile.InstanceTypeService.InstanceTypeStateExpanded;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link InstanceTypeService} class.
 */
public class InstanceTypeServiceTest extends ComputeBaseTest {
    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(InstanceTypeFactoryService.SELF_LINK);
    }

    @Test
    public void testExpanded() throws Throwable {
        InstanceTypeState instanceType = createInstanceType();

        InstanceTypeState retrievedInstanceType = getDocument(InstanceTypeState.class, instanceType.documentSelfLink);
        assertEquals("instanceType-1", retrievedInstanceType.name);

        InstanceTypeStateExpanded retrievedExpandedInstanceType = getDocument(InstanceTypeStateExpanded.class,
                instanceType.documentSelfLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);

        assertEquals(instanceType.documentSelfLink, retrievedExpandedInstanceType.documentSelfLink);
        assertEquals(1, retrievedExpandedInstanceType.tags.size());
    }

    @Test
    public void testPut() throws Throwable {
        InstanceTypeState instanceType = createInstanceType();

        InstanceTypeState retrievedInstanceType = getDocument(InstanceTypeState.class, instanceType.documentSelfLink);
        retrievedInstanceType.name = "instanceType-2";

        InstanceTypeState updatedInsntaceType = doPut(retrievedInstanceType);
        assertEquals("instanceType-2", updatedInsntaceType.name);
    }

    private TagState createTag(String key, String value) throws Throwable {
        TagState tagState = new TagState();
        tagState.key = key;
        tagState.value = value;
        return doPost(tagState, TagService.FACTORY_LINK);
    }


    private InstanceTypeState createInstanceType() throws Throwable {
        TagState tagState = createTag("tagkey-1" , "tagvalue-1");

        InstanceTypeState instanceType = new InstanceTypeState();
        instanceType.name = "instanceType-1";
        instanceType.endpointType = EndpointType.vsphere.name();
        instanceType.tagLinks = Collections.singleton(tagState.documentSelfLink);

        instanceType = doPost(instanceType, InstanceTypeFactoryService.SELF_LINK);
        return instanceType;
    }

}
