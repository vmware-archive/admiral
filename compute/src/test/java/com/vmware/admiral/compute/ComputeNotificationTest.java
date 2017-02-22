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

package com.vmware.admiral.compute;

import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class ComputeNotificationTest extends ComputeBaseTest {
    public static final String COMPUTE_ID = "test-host-compute-id";
    public static final String COMPUTE_ADDRESS = "somehost";
    public static final String COMPUTE_DESC_ID = "test-host-compute-desc-id";
    public static final String RESOURCE_POOL_ID = "test-host-resource-pool";
    private CompositeComponent compositeComponent;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(CompositeComponentFactoryService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
    }

    @Test
    public void testCompute() throws Throwable {
        compositeComponent = createCompositeComponent();
        ComputeState comp1 = createComputeHost(compositeComponent.documentSelfLink);

        // add a new container:
        waitFor(() -> {
            compositeComponent = getDocument(CompositeComponent.class,
                    compositeComponent.documentSelfLink);
            if (compositeComponent.componentLinks == null
                    || compositeComponent.componentLinks.isEmpty()) {
                return false;
            }

            if (compositeComponent.componentLinks.size() != 1) {
                return false;
            }
            String containerLink = compositeComponent.componentLinks.get(0);

            return comp1.documentSelfLink.equals(containerLink);
        });

        ComputeState comp2 = createComputeHost(compositeComponent.documentSelfLink);


        // add a second new container:
        waitFor(() -> {
            compositeComponent = getDocument(CompositeComponent.class,
                    compositeComponent.documentSelfLink);

            int count = 0;
            List<String> computeLinks = new ArrayList<>(Arrays.asList(comp1.documentSelfLink,
                    comp2.documentSelfLink));
            for (String componentLink : compositeComponent.componentLinks) {
                if (computeLinks.remove(componentLink)) {
                    count++;
                    continue;
                }
                fail("Unknown componentLink found:" + componentLink + ", expected :"
                        + computeLinks);
            }

            return count == 2;
        });

        // delete a container:
        delete(comp2.documentSelfLink);

        waitFor(() -> {
            compositeComponent = getDocument(CompositeComponent.class,
                    compositeComponent.documentSelfLink);
            if (compositeComponent.componentLinks.size() != 1) {
                return false;
            }
            String componentLink = compositeComponent.componentLinks.get(0);

            return comp1.documentSelfLink.equals(componentLink);
        });

        // update a container to remove the composite link
        comp1.customProperties.remove(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY);

        doOperation(comp1, UriUtils.buildUri(host, comp1.documentSelfLink),
                false, Action.PUT);

        // test if the CompositeComponent has been deleted.
        waitFor(() -> {
            ServiceDocumentQuery<CompositeComponent> query = new ServiceDocumentQuery<>(host,
                    CompositeComponent.class);
            AtomicBoolean deleted = new AtomicBoolean();
            host.testStart(1);
            query.queryDocument(compositeComponent.documentSelfLink, (r) -> {
                if (r.hasException()) {
                    host.failIteration(r.getException());
                } else if (r.hasResult()) {
                    deleted.set(false);
                    host.completeIteration();
                } else {
                    deleted.set(true);
                    host.completeIteration();
                }
            });
            host.testWait();
            return deleted.get();
        });

        // create compositeComponent again:
        compositeComponent = createCompositeComponent();

        // update a container to add the composite link
        comp1.customProperties.put(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                compositeComponent.documentSelfLink);
        doOperation(comp1, UriUtils.buildUri(host, comp1.documentSelfLink),
                false, Action.PATCH);

        waitFor(() -> {
            compositeComponent = getDocument(CompositeComponent.class,
                    compositeComponent.documentSelfLink);

            if (compositeComponent.componentLinks == null
                    || compositeComponent.componentLinks.size() != 1) {
                return false;
            }
            String componentLink = compositeComponent.componentLinks.get(0);

            return comp1.documentSelfLink.equals(componentLink);
        });

        delete(comp1.documentSelfLink);

        // test if the CompositeComponent has been deleted.
        waitFor(() -> {
            ServiceDocumentQuery<CompositeComponent> query = new ServiceDocumentQuery<>(host,
                    CompositeComponent.class);
            AtomicBoolean deleted = new AtomicBoolean();
            host.testStart(1);
            query.queryDocument(compositeComponent.documentSelfLink, (r) -> {
                if (r.hasException()) {
                    host.failIteration(r.getException());
                } else if (r.hasResult()) {
                    deleted.set(false);
                    host.completeIteration();
                } else {
                    deleted.set(true);
                    host.completeIteration();
                }
            });
            host.testWait();
            return deleted.get();
        });
    }

    private CompositeComponent createCompositeComponent() throws Throwable {
        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.name = "test-name";
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService.SELF_LINK);
        return compositeComponent;
    }

    private ComputeState createComputeHost(String compositeComponentLink) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = COMPUTE_ADDRESS; // this will be used for ssh to access the host
        cs.powerState = PowerState.ON;
        cs.descriptionLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                COMPUTE_DESC_ID);
        cs.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                RESOURCE_POOL_ID);
        cs.adapterManagementReference = URI.create("http://localhost:8081");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                compositeComponentLink);

        ComputeState created = doPost(cs, ComputeService.FACTORY_LINK);

        return created;
    }
}
