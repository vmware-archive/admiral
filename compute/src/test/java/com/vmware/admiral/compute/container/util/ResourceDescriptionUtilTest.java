/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

public class ResourceDescriptionUtilTest extends ComputeBaseTest {

    private HashSet<String> descriptionLinks = new HashSet<>();
    private HashSet<String> stateLinks = new HashSet<>();

    @After
    public void tearDown() {
        cleanupDocuments(stateLinks);
        cleanupDocuments(descriptionLinks);
    }

    private void cleanupDocuments(Collection<String> documentLinks) {
        if (documentLinks != null && !documentLinks.isEmpty()) {
            for (Iterator<String> iterator = documentLinks.iterator(); iterator.hasNext();) {
                String documentLink = iterator.next();
                try {
                    host.log(Level.INFO, "Cleanup: deleting %s", documentLink);
                    delete(documentLink);
                } catch (Throwable ex) {
                    host.log(Level.WARNING,
                            "Failed to cleanup document [%s]: %s",
                            documentLink,
                            Utils.toString(ex));
                }
            }
        }
    }

    @Test
    public void testDeleteUnusedContainerDescriptionShouldSucceed() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        deleteResourceDescription(containerDescription.documentSelfLink,
                ContainerDescription.class);
    }

    @Test
    public void testDeleteUnusedNetworkDescriptionShouldSucceed() throws Throwable {
        ContainerNetworkDescription networkDescription = createNetworkDescription();
        deleteResourceDescription(networkDescription.documentSelfLink,
                ContainerNetworkDescription.class);
    }

    @Test
    public void testDeleteUnusedVolumeDescriptionShouldSucceed() throws Throwable {
        ContainerVolumeDescription volumeDescription = createVolumeDescription();
        deleteResourceDescription(volumeDescription.documentSelfLink,
                ContainerVolumeDescription.class);
    }

    @Test
    public void testDeleteUnusedClonedCompositeDescriptionShouldSucceed() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        ContainerNetworkDescription networkDescription = createNetworkDescription();
        ContainerVolumeDescription volumeDescription = createVolumeDescription();
        CompositeDescription clonedCompositeDescription = createClonedCompositeDescription(
                containerDescription.documentSelfLink,
                networkDescription.documentSelfLink,
                volumeDescription.documentSelfLink);
        deleteClonedCompositeDescription(clonedCompositeDescription.documentSelfLink,
                CompositeDescription.class);
        verifyDocumentDoesNotExist(containerDescription.documentSelfLink,
                ContainerDescription.class);
        verifyDocumentDoesNotExist(networkDescription.documentSelfLink,
                ContainerNetworkDescription.class);
        verifyDocumentDoesNotExist(volumeDescription.documentSelfLink,
                ContainerVolumeDescription.class);
    }

    @Test
    public void testDeleteContainerDescriptionInUseShouldFail() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        createContainerState(containerDescription.documentSelfLink);

        try {
            deleteResourceDescription(containerDescription.documentSelfLink,
                    ContainerDescription.class);
            fail("Expected container description deletion to fail because a container state is using it.");
        } catch (IllegalStateException ex) {
            String expected = String.format(
                    ResourceDescriptionUtil.RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT,
                    1, containerDescription.documentSelfLink);
            assertTrue(String.format("Expected error to contain string [%s]. Actual error: %s",
                    expected, ex.getMessage()), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void testDeleteNetworkDescriptionInUseShouldFail() throws Throwable {
        ContainerNetworkDescription networkDescription = createNetworkDescription();
        createNetworkState(networkDescription.documentSelfLink);

        try {
            deleteResourceDescription(networkDescription.documentSelfLink,
                    ContainerNetworkDescription.class);
            fail("Expected network description deletion to fail because a network state is using it.");
        } catch (IllegalStateException ex) {
            String expected = String.format(
                    ResourceDescriptionUtil.RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT,
                    1, networkDescription.documentSelfLink);
            assertTrue(String.format("Expected error to contain string [%s]. Actual error: %s",
                    expected, ex.getMessage()), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void testDeleteVolumeDescriptionInUseShouldFail() throws Throwable {
        ContainerVolumeDescription volumeDescription = createVolumeDescription();
        createVolumeState(volumeDescription.documentSelfLink);

        try {
            deleteResourceDescription(volumeDescription.documentSelfLink,
                    ContainerNetworkDescription.class);
            fail("Expected volume description deletion to fail because a volume state is using it.");
        } catch (IllegalStateException ex) {
            String expected = String.format(
                    ResourceDescriptionUtil.RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT,
                    1, volumeDescription.documentSelfLink);
            assertTrue(String.format("Expected error to contain string [%s]. Actual error: %s",
                    expected, ex.getMessage()), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void testDeleteClonedCompositeDescriptionInUseShouldFail() throws Throwable {
        CompositeDescription compositeDescription = createClonedCompositeDescription(
                "/fake-component-link");
        createCompositeComponent(compositeDescription.documentSelfLink);

        try {
            deleteClonedCompositeDescription(compositeDescription.documentSelfLink,
                    CompositeDescription.class);
            fail("Expected composite description deletion to fail because a composite component is using it.");
        } catch (IllegalStateException ex) {
            String expected = String.format(
                    ResourceDescriptionUtil.RESOURCE_DESCRIPTION_IN_USE_ERROR_FORMAT,
                    1, compositeDescription.documentSelfLink);
            assertTrue(String.format("Expected error to contain string [%s]. Actual error: %s",
                    expected, ex.getMessage()), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void testDeleteCompositeDescriptionShouldFail() throws Throwable {
        CompositeDescription compositeDescription = createCompositeDescription(
                "/fake-component-link");

        try {
            deleteClonedCompositeDescription(compositeDescription.documentSelfLink,
                    CompositeDescription.class);
            fail("Expected composite description deletion to fail because it is not a cloned composite description.");
        } catch (IllegalArgumentException ex) {
            String expected = String.format(
                    ResourceDescriptionUtil.NOT_CLONED_COMPOSITE_DESCRIPTION_ERROR_FORMAT,
                    compositeDescription.documentSelfLink);
            assertTrue(String.format("Expected error to contain string [%s]. Actual error: %s",
                    expected, ex.getMessage()), ex.getMessage().contains(expected));
        }
    }

    @Test
    public void testDeleteMissingDescriptionShouldPass() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        deleteResourceDescription(containerDescription.documentSelfLink,
                ContainerDescription.class);

        host.testStart(1);
        ResourceDescriptionUtil
                .deleteResourceDescription(host, containerDescription.documentSelfLink)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE,
                                "Expected deletion of missing resource not to fail.");
                        host.failIteration(ex);
                    } else {
                        host.completeIteration();
                    }
                });
        host.testWait();
    }

    private void deleteResourceDescription(String descriptionLink,
            Class<? extends ServiceDocument> descriptionClass) throws Throwable {
        deleteResourceOrClonedCompositeDescription(descriptionLink, descriptionClass,
                ResourceDescriptionUtil::deleteResourceDescription);
    }

    private void deleteClonedCompositeDescription(String descriptionLink,
            Class<? extends ServiceDocument> descriptionClass) throws Throwable {
        deleteResourceOrClonedCompositeDescription(descriptionLink, descriptionClass,
                ResourceDescriptionUtil::deleteClonedCompositeDescription);
    }

    private void deleteResourceOrClonedCompositeDescription(String descriptionLink,
            Class<? extends ServiceDocument> descriptionClass,
            BiFunction<ServiceHost, String, DeferredResult<Void>> deleteFunction) throws Throwable {
        // verify description exists
        verifyDocumentExists(descriptionLink, descriptionClass);

        // delete it
        host.testStart(1);

        deleteFunction.apply(host, descriptionLink)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex instanceof CompletionException ? ex.getCause() : ex);
                    } else {
                        host.completeIteration();
                    }
                });
        host.testWait();

        // verify it is not there anymore
        verifyDocumentDoesNotExist(descriptionLink, descriptionClass);

        // remove it from the cleanup list
        descriptionLinks.remove(descriptionLink);
    }

    private void verifyDocumentExists(String documentLink, Class<?> documentClass)
            throws Throwable {
        String error = String.format(
                "Document %s was expected to exist but it doesn't.",
                documentLink);
        assertNotNull(error, getDocumentNoWait(documentClass, documentLink));
    }

    private void verifyDocumentDoesNotExist(String documentLink, Class<?> documentClass)
            throws Throwable {
        String error = String.format(
                "Document %s was expected not to exist but it does.",
                documentLink);
        assertNull(error, getDocumentNoWait(documentClass, documentLink));
    }

    private ContainerDescription createContainerDescription() throws Throwable {
        ContainerDescription desc = new ContainerDescription();
        desc.name = "test-container-description";
        desc.image = "test-container-image";
        waitForServiceAvailability(ContainerDescriptionFactoryService.SELF_LINK);
        ContainerDescription result = getOrCreateDocument(desc,
                ContainerDescriptionFactoryService.SELF_LINK);
        descriptionLinks.add(result.documentSelfLink);
        return result;
    }

    private ContainerState createContainerState(String descriptionLink) throws Throwable {
        ContainerState state = new ContainerState();
        state.name = "test-container-state";
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        ContainerState result = getOrCreateDocument(state, ContainerFactoryService.SELF_LINK);
        stateLinks.add(result.documentSelfLink);
        return result;
    }

    private ContainerNetworkDescription createNetworkDescription() throws Throwable {
        ContainerNetworkDescription desc = new ContainerNetworkDescription();
        desc.name = "test-network-description";
        waitForServiceAvailability(ContainerNetworkDescriptionFactoryService.SELF_LINK);
        ContainerNetworkDescription result = getOrCreateDocument(desc,
                ContainerNetworkDescriptionFactoryService.SELF_LINK);
        descriptionLinks.add(result.documentSelfLink);
        return result;
    }

    private ContainerNetworkState createNetworkState(String descriptionLink) throws Throwable {
        ContainerNetworkState state = new ContainerNetworkState();
        state.name = "test-network-state";
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerNetworkFactoryService.SELF_LINK);
        ContainerNetworkState result = getOrCreateDocument(state,
                ContainerNetworkFactoryService.SELF_LINK);
        stateLinks.add(result.documentSelfLink);
        return result;
    }

    private ContainerVolumeDescription createVolumeDescription() throws Throwable {
        ContainerVolumeDescription desc = new ContainerVolumeDescription();
        desc.name = "test-volume-description";
        waitForServiceAvailability(ContainerVolumeDescriptionFactoryService.SELF_LINK);
        ContainerVolumeDescription result = getOrCreateDocument(desc,
                ContainerVolumeDescriptionFactoryService.SELF_LINK);
        descriptionLinks.add(result.documentSelfLink);
        return result;
    }

    private ContainerVolumeState createVolumeState(String descriptionLink) throws Throwable {
        ContainerVolumeState state = new ContainerVolumeState();
        state.name = "test-volume-state";
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerVolumeFactoryService.SELF_LINK);
        ContainerVolumeState result = getOrCreateDocument(state,
                ContainerVolumeFactoryService.SELF_LINK);
        stateLinks.add(result.documentSelfLink);
        return result;
    }

    private CompositeDescription createCompositeDescription(String... componentLinks)
            throws Throwable {
        CompositeDescription desc = prepareCompositeDescription(componentLinks);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        CompositeDescription result = getOrCreateDocument(desc,
                CompositeDescriptionFactoryService.SELF_LINK);
        descriptionLinks.add(result.documentSelfLink);
        return result;
    }

    private CompositeDescription createClonedCompositeDescription(String... componentLinks)
            throws Throwable {
        CompositeDescription desc = prepareCompositeDescription(componentLinks);
        desc.parentDescriptionLink = "fake-parent-link";
        CompositeDescription result = getOrCreateDocument(desc,
                CompositeDescriptionFactoryService.SELF_LINK);
        descriptionLinks.add(result.documentSelfLink);
        return result;
    }

    private CompositeDescription prepareCompositeDescription(String... componentLinks)
            throws Throwable {
        CompositeDescription desc = new CompositeDescription();
        desc.name = "test-composite-description";
        desc.descriptionLinks = Arrays.asList(componentLinks);
        return desc;
    }

    private CompositeComponent createCompositeComponent(String descriptionLink) throws Throwable {
        CompositeComponent state = new CompositeComponent();
        state.name = "test-composite-component";
        state.compositeDescriptionLink = descriptionLink;
        waitForServiceAvailability(CompositeComponentFactoryService.SELF_LINK);
        CompositeComponent result = getOrCreateDocument(state,
                CompositeComponentFactoryService.SELF_LINK);
        stateLinks.add(result.documentSelfLink);
        return result;
    }
}
