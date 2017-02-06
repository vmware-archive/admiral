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

package com.vmware.admiral.compute.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.kubernetes.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesDescrtiptionContentServiceTest extends ComputeBaseTest {
    private String sampleYamlDefinition = "---\n"
            + "apiVersion: v1\n"
            + "kind: Service\n"
            + "metadata:\n"
            + "  name: wordpress\n"
            + "  labels:\n"
            + "    app: wordpress\n"
            + "spec:\n"
            + "  ports:\n"
            + "  - port: 80\n"
            + "  selector:\n"
            + "    app: wordpress\n"
            + "    tier: frontend\n";

    private String sampleYamlDefinitionInvalid = "---\n"
            + "apiVersion: v1\n"
            + "#kind: Service\n"
            + "metadata:\n"
            + "  name: wordpress\n"
            + "  labels:\n"
            + "    app: wordpress\n"
            + "spec:\n"
            + "  ports:\n"
            + "  - port: 80\n"
            + "  selector:\n"
            + "    app: wordpress\n"
            + "    tier: frontend\n";

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(KubernetesDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(KubernetesDescriptionContentService.SELF_LINK);
    }

    @Test
    public void testCreateKubernetesDescriptionsFromMultipleYamls() {
        StringBuilder multiYaml = new StringBuilder();
        multiYaml.append(sampleYamlDefinition);
        multiYaml.append(sampleYamlDefinition);
        multiYaml.append(sampleYamlDefinition);

        Operation op = Operation.createPost(UriUtils.buildUri(host,
                KubernetesDescriptionContentService.SELF_LINK))
                .setBody(multiYaml.toString())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating k8s descriptions failed: %s", Utils.toString(ex));
                        host.failIteration(ex);
                        return;
                    } else {
                        String[] resourceLinks = o.getBody(String[].class);
                        assertEquals(3, resourceLinks.length);
                        Arrays.stream(resourceLinks).forEach(r -> assertNotNull(r));
                        host.completeIteration();
                    }
                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testCreateKubernetesDescriptionsWithOneInvalidShouldFail() throws Throwable {
        StringBuilder multiYaml = new StringBuilder();
        multiYaml.append(sampleYamlDefinition);
        multiYaml.append(sampleYamlDefinitionInvalid);
        multiYaml.append(sampleYamlDefinition);

        Operation op = Operation.createPost(UriUtils.buildUri(host,
                KubernetesDescriptionContentService.SELF_LINK))
                .setBody(multiYaml.toString())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating k8s descriptions failed: %s", Utils.toString(ex));
                        try {
                            verifyNoLeftovers();
                        } catch (Throwable e) {
                            host.failIteration(e);
                        }
                        host.completeIteration();
                    } else {
                        host.failIteration(new IllegalStateException("Operation had to fail but "
                                + "it succeeded."));
                    }
                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testCreateKubernetesDescriptionWithAllInvalidShouldFail() {
        StringBuilder multiYaml = new StringBuilder();
        multiYaml.append(sampleYamlDefinitionInvalid);
        multiYaml.append(sampleYamlDefinitionInvalid);
        multiYaml.append(sampleYamlDefinitionInvalid);

        Operation op = Operation.createPost(UriUtils.buildUri(host,
                KubernetesDescriptionContentService.SELF_LINK))
                .setBody(multiYaml.toString())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating k8s descriptions failed: %s", Utils.toString(ex));
                        host.completeIteration();
                        return;
                    } else {
                        String[] resourceLinks = o.getBody(String[].class);
                        host.failIteration(
                                new IllegalStateException(String.format("Operation had to fail and "
                                        + "no descriptions to be created. Count of resources created: %d,"
                                        + " resources: %s", resourceLinks.length, String.join(",",
                                        resourceLinks))));
                    }
                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    private void verifyNoLeftovers() throws Throwable {
        List<String> leftovers = getDocumentLinksOfType(KubernetesDescription.class);
        assertEquals(0, leftovers.size());
    }
}
