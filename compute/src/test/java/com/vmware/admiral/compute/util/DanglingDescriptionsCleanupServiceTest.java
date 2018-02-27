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

package com.vmware.admiral.compute.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

public class DanglingDescriptionsCleanupServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(DanglingDescriptionsCleanupService.SELF_LINK);
    }

    @Test
    public void testCleanupDanglingDescriptions() throws Throwable {
        host.log(Level.INFO, "Creating real content...");
        Collection<String> realContentLinks = buildRealContent();
        host.log(Level.INFO, "Verifying real content exists...");
        verifyDocumentsExist(realContentLinks);

        host.log(Level.INFO, "Creating dangling descriptions...");
        Collection<String> danglingDescriptionLinks = buildDanglingDescriptions();
        host.log(Level.INFO, "Verifying dangling descriptions exist...");
        verifyDocumentsExist(danglingDescriptionLinks);

        host.log(Level.INFO, "Sending POST to %s...", DanglingDescriptionsCleanupService.SELF_LINK);

        host.testStart(1);
        Operation.createPost(host, DanglingDescriptionsCleanupService.SELF_LINK)
                .setReferer("/dangling-descriptions-cleanup-service-test")
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Cleanup failed: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        host.log(Level.INFO, "Cleanup finished successfully");
                        host.completeIteration();
                    }
                }).sendWith(host);
        host.testWait();

        host.log(Level.INFO, "Verifying dangling descriptions are deleted...");
        verifyDocumentsDoNotExist(danglingDescriptionLinks);

        host.log(Level.INFO, "Verifying real content still exists...");
        verifyDocumentsExist(realContentLinks);
    }

    private void verifyDocumentsExist(Collection<String> documentLinks) throws Throwable {
        for (String documentLink : documentLinks) {
            verifyDocumentExists(documentLink);
        }
    }

    private void verifyDocumentsDoNotExist(Collection<String> documentLinks) throws Throwable {
        for (String documentLink : documentLinks) {
            verifyDocumentDoesNotExist(documentLink);
        }
    }

    private void verifyDocumentExists(String documentLink) throws Throwable {
        ServiceDocument document = getDocumentNoWait(ServiceDocument.class, documentLink);
        try {
            assertNotNull(String.format("Expected document [%s] to exist.", documentLink),
                    document);
        } catch (AssertionError e) {
            host.log(Level.SEVERE, Utils.toString(e));
            throw e;
        }
    }

    private void verifyDocumentDoesNotExist(String documentLink) throws Throwable {
        ServiceDocument document = getDocumentNoWait(ServiceDocument.class, documentLink);
        try {
            assertNull(String.format("Expected document [%s] not to exist.", documentLink),
                    document);
        } catch (AssertionError e) {
            host.log(Level.SEVERE, Utils.toString(e));
            throw e;
        }
    }

    private Collection<String> buildRealContent() throws Throwable {
        List<String> documentLinks = new ArrayList<>();

        ContainerDescription cd = createContainerDescription("real-container-description");
        ContainerState cs = createContainerState("real-container-state", cd.documentSelfLink);
        documentLinks.add(cd.documentSelfLink);
        documentLinks.add(cs.documentSelfLink);

        ContainerNetworkDescription nd = createNetworkDescription("real-network-description");
        ContainerNetworkState ns = createNetworkState("real-network-state", nd.documentSelfLink);
        documentLinks.add(nd.documentSelfLink);
        documentLinks.add(ns.documentSelfLink);

        ContainerVolumeDescription vd = createVolumeDescription("real-volume-description");
        ContainerVolumeState vs = createContainerVolumeState("real-volume-state",
                vd.documentSelfLink);
        documentLinks.add(vd.documentSelfLink);
        documentLinks.add(vs.documentSelfLink);

        CompositeDescription ccd = createClonedCompositeDescription("real-application-description",
                "/fake-component-link");
        CompositeComponent ccc = createCompositeComponent("real-application-state",
                ccd.documentSelfLink);
        documentLinks.add(ccd.documentSelfLink);
        documentLinks.add(ccc.documentSelfLink);

        documentLinks.add(createCompositeDescription("not-cloned-component-description",
                "/antoher-fake-link").documentSelfLink);

        return documentLinks;
    }

    private Collection<String> buildDanglingDescriptions() throws Throwable {
        List<String> documentLinks = new ArrayList<>();

        documentLinks
                .add(createContainerDescription("dangling-container-description").documentSelfLink);
        documentLinks
                .add(createNetworkDescription("dangling-network-description").documentSelfLink);
        documentLinks.add(createVolumeDescription("dangling-volume-description").documentSelfLink);
        documentLinks.add(createClonedCompositeDescription("dangling-composite-description",
                "/fake-component-link").documentSelfLink);

        return documentLinks;
    }

    private ContainerDescription createContainerDescription(String name) throws Throwable {
        ContainerDescription desc = new ContainerDescription();
        desc.name = randomName(name);
        desc.image = "fake-image";
        waitForServiceAvailability(ContainerDescriptionFactoryService.SELF_LINK);
        return getOrCreateDocument(desc, ContainerDescriptionFactoryService.SELF_LINK);
    }

    private ContainerNetworkDescription createNetworkDescription(String name) throws Throwable {
        ContainerNetworkDescription desc = new ContainerNetworkDescription();
        desc.name = randomName(name);
        waitForServiceAvailability(ContainerNetworkDescriptionFactoryService.SELF_LINK);
        return getOrCreateDocument(desc, ContainerNetworkDescriptionFactoryService.SELF_LINK);
    }

    private ContainerVolumeDescription createVolumeDescription(String name) throws Throwable {
        ContainerVolumeDescription desc = new ContainerVolumeDescription();
        desc.name = randomName(name);
        waitForServiceAvailability(ContainerVolumeDescriptionFactoryService.SELF_LINK);
        return getOrCreateDocument(desc, ContainerVolumeDescriptionFactoryService.SELF_LINK);
    }

    private CompositeDescription createCompositeDescription(String name, String... componentLinks)
            throws Throwable {
        return createCompositeDescription(name, false, componentLinks);
    }

    private CompositeDescription createClonedCompositeDescription(String name,
            String... componentLinks) throws Throwable {
        return createCompositeDescription(name, true, componentLinks);
    }

    private CompositeDescription createCompositeDescription(String name, boolean cloned,
            String... componentLinks) throws Throwable {
        CompositeDescription desc = new CompositeDescription();
        desc.name = randomName(name);
        desc.descriptionLinks = Arrays.asList(componentLinks);
        if (cloned) {
            desc.parentDescriptionLink = "/fake-parent-link";
        }
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        return getOrCreateDocument(desc, CompositeDescriptionFactoryService.SELF_LINK);
    }

    private ContainerState createContainerState(String name, String descriptionLink)
            throws Throwable {
        ContainerState state = new ContainerState();
        state.name = randomName(name);
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        return getOrCreateDocument(state, ContainerFactoryService.SELF_LINK);
    }

    private ContainerNetworkState createNetworkState(String name, String descriptionLink)
            throws Throwable {
        ContainerNetworkState state = new ContainerNetworkState();
        state.name = randomName(name);
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerNetworkFactoryService.SELF_LINK);
        return getOrCreateDocument(state, ContainerNetworkFactoryService.SELF_LINK);
    }

    private ContainerVolumeState createContainerVolumeState(String name, String descriptionLink)
            throws Throwable {
        ContainerVolumeState state = new ContainerVolumeState();
        state.name = randomName(name);
        state.descriptionLink = descriptionLink;
        waitForServiceAvailability(ContainerVolumeFactoryService.SELF_LINK);
        return getOrCreateDocument(state, ContainerVolumeFactoryService.SELF_LINK);
    }

    private CompositeComponent createCompositeComponent(String name, String descriptionLink)
            throws Throwable {
        CompositeComponent state = new CompositeComponent();
        state.name = randomName(name);
        state.compositeDescriptionLink = descriptionLink;
        waitForServiceAvailability(CompositeComponentFactoryService.SELF_LINK);
        return getOrCreateDocument(state, CompositeComponentFactoryService.SELF_LINK);
    }

    public String randomName(String basename) {
        return String.format("%s-%s", basename, UUID.randomUUID().toString());
    }
}
