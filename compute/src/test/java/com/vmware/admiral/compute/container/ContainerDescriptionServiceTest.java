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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerDescriptionServiceTest extends ComputeBaseTest {

    private static final String VOLUME_DRIVER = "flocker";

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
    }

    @Test
    public void testContainerDescriptionServices() throws Throwable {
        verifyService(
                FactoryService.create(ContainerDescriptionService.class),
                ContainerDescription.class,
                (prefix, index) -> {
                    ContainerDescription containerDesc = new ContainerDescription();
                    containerDesc.name = prefix + "name" + index;
                    containerDesc.image = prefix + "image:latest" + index;
                    containerDesc.imageReference = URI.create("http://docker/image" + index);
                    containerDesc.customProperties = new HashMap<>();
                    containerDesc.volumeDriver = VOLUME_DRIVER;

                    return containerDesc;
                },
                (prefix, serviceDocument) -> {
                    ContainerDescription contDesc = (ContainerDescription) serviceDocument;
                    assertTrue(contDesc.image.startsWith(prefix + "image:latest"));
                    assertTrue(contDesc.name.startsWith(prefix + "name"));
                    assertTrue(
                            contDesc.imageReference.toString().startsWith("http://docker/image"));
                    assertTrue(
                            contDesc.volumeDriver.equals(VOLUME_DRIVER));
                });
    }

    // VBV-1845
    @Test
    public void testInvalidJsonContainerDescription() throws Throwable {
        InvalidDescription state = new InvalidDescription();
        state.volumes = "test";
        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(state)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK)
                .setReferer(URI.create("/"))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(new Exception(
                                "Migration request for invalid json should be successful"));
                        return;
                    } else {
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.sendRequest(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWithInvalidMemoryLimit() throws Throwable {
        ContainerDescription contDesc = createContainerDescription();
        contDesc.memoryLimit = -1L;

        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(contDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when memory limit is invalid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWithInvalidMemorySwap() throws Throwable {
        ContainerDescription contDesc = createContainerDescription();
        contDesc.memorySwapLimit = -2L;

        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(contDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when memory swap is invalid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWithInvalidMaximumRetryCount() throws Throwable {
        ContainerDescription contDesc = createContainerDescription();
        contDesc.maximumRetryCount = -1;

        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(contDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when maximum retry count is valid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWithInvalidNetworkMode() throws Throwable {
        ContainerDescription contDesc = createContainerDescription();
        contDesc.networkMode = "invalid";

        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(contDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when network mode is valid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWithInvalidRestartPolicy() throws Throwable {
        ContainerDescription contDesc = createContainerDescription();
        contDesc.restartPolicy = "invalid";

        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(contDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when restart policy is valid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testDeleteContainerDescription() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        containerDescription.name = "containerDescription";

        containerDescription = doPost(containerDescription,
                ContainerDescriptionService.FACTORY_LINK);

        delete(containerDescription.documentSelfLink);

        List<String> resourceLinks = findResourceLinks(ContainerDescription.class,
                Arrays.asList(containerDescription.documentSelfLink));

        Assert.assertEquals("Container description must be deleted on DELETE", 0,
                resourceLinks.size());
    }

    @Test
    public void testDeleteContainerDescPartOfComposite() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();
        containerDescription.name = "containerDescription";

        containerDescription = doPost(containerDescription,
                ContainerDescriptionService.FACTORY_LINK);

        CompositeDescription composite = new CompositeDescription();
        composite.name = "composite";
        composite.descriptionLinks = Collections
                .singletonList(containerDescription.documentSelfLink);
        composite = doPost(composite, CompositeDescriptionFactoryService.SELF_LINK);

        delete(containerDescription.documentSelfLink);

        List<String> resourceLinks = findResourceLinks(ContainerDescription.class,
                Arrays.asList(containerDescription.documentSelfLink));

        Assert.assertEquals("Container description must be deleted on DELETE", 0,
                resourceLinks.size());
    }

    @Test
    public void testDeleteContainerDescritpionWithLinks() throws Throwable {
        ContainerDescription containerDesc1 = createContainerDescription();
        containerDesc1.name = "containerDescription1";

        containerDesc1 = doPost(containerDesc1,
                ContainerDescriptionService.FACTORY_LINK);
        final String linkToDelete = containerDesc1.name;

        ContainerDescription containerDesc2 = createContainerDescription();
        containerDesc2.name = "containerDescription2";
        containerDesc2.links = new String[] { String.format("%s:%s",
                containerDesc1.name, "alias"), String.format("%s:%s", "any", "alias2") };

        containerDesc2 = doPost(containerDesc2,
                ContainerDescriptionService.FACTORY_LINK);
        final String testContainerLink = containerDesc2.documentSelfLink;

        CompositeDescription composite = new CompositeDescription();
        composite.name = "composite";
        composite.descriptionLinks = new ArrayList<>();
        composite.descriptionLinks.add(containerDesc1.documentSelfLink);
        composite.descriptionLinks.add(containerDesc2.documentSelfLink);

        composite = doPost(composite, CompositeDescriptionFactoryService.SELF_LINK);

        delete(containerDesc1.documentSelfLink);

        waitFor(() -> {
            ContainerDescription desc = getDocument(ContainerDescription.class,
                    testContainerLink);

            if ((desc.links.length > 1) || (desc.dependsOn.length > 1)) {
                return false;
            } else if (desc.links.length == 1
                    && !desc.links[0].split(":")[0].equals(linkToDelete)
                    && desc.dependsOn.length == 1
                    && !desc.dependsOn[0].equals(linkToDelete)) {
                return true;
            } else {
                throw new IllegalStateException(
                        "Links to delete container description must be deleted.");
            }

        });
    }

    @Test
    public void testDeleteContainerDescritpionWithLinksAndMultipleComposites() throws Throwable {
        ContainerDescription sharedDesc = createContainerDescription();
        sharedDesc.name = "sharedDesc";
        final String linkToDelete = sharedDesc.name;

        sharedDesc = doPost(sharedDesc,
                ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription containerDescComposite1 = createContainerDescription();
        containerDescComposite1.name = "containerDescription1";
        containerDescComposite1.links = new String[] { String.format("%s:%s",
                sharedDesc.name, "alias"), String.format("%s:%s", "any", "alias2") };

        containerDescComposite1 = doPost(containerDescComposite1,
                ContainerDescriptionService.FACTORY_LINK);
        final String testContainerComposite1 = containerDescComposite1.documentSelfLink;

        ContainerDescription containerDescComposite2 = createContainerDescription();
        containerDescComposite2.name = "containerDescription2";
        containerDescComposite2.links = new String[] { String.format("%s:%s",
                sharedDesc.name, "alias"), String.format("%s:%s", "any", "alias2") };

        containerDescComposite2 = doPost(containerDescComposite2,
                ContainerDescriptionService.FACTORY_LINK);
        final String testContainerComposite2 = containerDescComposite2.documentSelfLink;

        CompositeDescription composite1 = new CompositeDescription();
        composite1.name = "composite1";
        composite1.descriptionLinks = new ArrayList<>();
        composite1.descriptionLinks.add(containerDescComposite1.documentSelfLink);
        composite1.descriptionLinks.add(sharedDesc.documentSelfLink);

        composite1 = doPost(composite1, CompositeDescriptionFactoryService.SELF_LINK);

        CompositeDescription composite2 = new CompositeDescription();
        composite2.name = "composite1";
        composite2.descriptionLinks = new ArrayList<>();
        composite2.descriptionLinks.add(sharedDesc.documentSelfLink);
        composite2.descriptionLinks.add(containerDescComposite2.documentSelfLink);

        composite2 = doPost(composite2, CompositeDescriptionFactoryService.SELF_LINK);

        delete(sharedDesc.documentSelfLink);

        waitFor(() -> {
            ContainerDescription desc = getDocument(ContainerDescription.class,
                    testContainerComposite1);

            if ((desc.links.length > 1) || (desc.dependsOn.length > 1)) {
                return false;
            } else if (desc.links.length == 1
                    && !desc.links[0].split(":")[0].equals(linkToDelete)
                    && desc.dependsOn.length == 1
                    && !desc.dependsOn[0].equals(linkToDelete)) {
                return true;
            } else {
                throw new IllegalStateException(
                        "Links to delete container description must be deleted.");
            }

        });

        waitFor(() -> {
            ContainerDescription desc = getDocument(ContainerDescription.class,
                    testContainerComposite2);

            if ((desc.links.length > 1) || (desc.dependsOn.length > 1)) {
                return false;
            } else if (desc.links.length == 1
                    && !desc.links[0].split(":")[0].equals(linkToDelete)
                    && desc.dependsOn.length == 1
                    && !desc.dependsOn[0].equals(linkToDelete)) {
                return true;
            } else {
                throw new IllegalStateException(
                        "Links to delete container description must be deleted.");
            }

        });
    }

    @Test
    public void testCannotAddSameVolumeMoreThanOnce() {
        String volumeToAdd = "volume:/var/storage";
        ContainerDescription createdContDesc = postContainerDescription(createContainerDescription());

        createdContDesc.volumes = new String[] {volumeToAdd, volumeToAdd};

        createdContDesc = patchContainerDescription(createdContDesc);

        assertEquals(1, createdContDesc.volumes.length);
        assertEquals(volumeToAdd, createdContDesc.volumes[0]);
    }

    private URI getContainerDescriptionUri() {
        return UriUtils.buildUri(host, ContainerDescriptionService.FACTORY_LINK);
    }

    private ContainerDescription createContainerDescription() {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.image = "image:latest";

        return containerDesc;
    }

    private ContainerDescription postContainerDescription(ContainerDescription containerDescription) {
        List<ContainerDescription> result = new LinkedList<>();
        Operation op = Operation.createPost(getContainerDescriptionUri())
                .setBody(containerDescription)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    result.add(o.getBody(ContainerDescription.class));
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();

        return result.get(0);
    }

    private ContainerDescription patchContainerDescription(ContainerDescription containerDescription) {
        List<ContainerDescription> result = new LinkedList<>();
        Operation op = Operation.createPatch(UriUtils.buildUri(host,containerDescription.documentSelfLink))
                .setBody(containerDescription)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    result.add(o.getBody(ContainerDescription.class));
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();

        return result.get(0);
    }

    private class InvalidDescription {
        // In the original description volumes are array
        public String volumes;
    }
}
