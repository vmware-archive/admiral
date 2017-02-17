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

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionContentService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesDescriptionServiceTest extends ComputeBaseTest {
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

    private String sampleYamlInvalidKubernetesDefinition = "---\n"
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
    public void testCreateKubernetesDescription() {
        KubernetesDescription description = new KubernetesDescription();
        description.kubernetesEntity = sampleYamlDefinition;

        Operation op = Operation.createPost(UriUtils.buildUri(host, KubernetesDescriptionService
                .FACTORY_LINK))
                .setBody(description)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating kubernetes description failed.");
                        host.failIteration(ex);
                        return;
                    } else {
                        KubernetesDescription desc = o.getBody(KubernetesDescription.class);
                        try {
                            assertEquals(description.kubernetesEntity, desc.kubernetesEntity);
                            assertEquals("Service", desc.type);
                            assertEquals(description.getKubernetesEntityAsJson(),
                                    desc.getKubernetesEntityAsJson());
                        } catch (Throwable e) {
                            host.log(Utils.toString(e));
                            host.failIteration(e);
                        }
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testCreateKubernetesDescriptionWithInvalidKubernetesShouldFail() {
        KubernetesDescription description = new KubernetesDescription();
        description.kubernetesEntity = sampleYamlInvalidKubernetesDefinition;

        Operation op = Operation.createPost(UriUtils.buildUri(host, KubernetesDescriptionService
                .FACTORY_LINK))
                .setBody(description)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating kubernetes description failed.");
                        host.completeIteration();
                        return;
                    } else {
                        host.failIteration(new IllegalStateException("Creation of Kubernetes "
                                + "Description with invalid yaml succeeded"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testCreateKubernetesDescriptionWithInvalidYamlInputShouldFail() {
        KubernetesDescription description = new KubernetesDescription();
        description.kubernetesEntity = "invalid\nyaml\ninput";

        Operation op = Operation.createPost(UriUtils.buildUri(host, KubernetesDescriptionService
                .FACTORY_LINK))
                .setBody(description)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log("Creating kubernetes description failed.");
                        if (!ex.getMessage().startsWith("Invalid YAML input.")) {
                            host.failIteration(
                                    new IllegalStateException(
                                            "Creation of kubernetes description failed with unexpected message: "
                                                    + ex.getMessage()));
                        } else {
                            host.completeIteration();
                            return;
                        }
                    } else {
                        host.failIteration(new IllegalStateException("Creation of Kubernetes "
                                + "Description with invalid yaml succeeded"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }
}
