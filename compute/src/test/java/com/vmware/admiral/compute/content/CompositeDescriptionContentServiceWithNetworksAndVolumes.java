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

package com.vmware.admiral.compute.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.UriUtils;

/**
 * Test the CompositeDescriptionContentService
 */
@RunWith(Parameterized.class)
public class CompositeDescriptionContentServiceWithNetworksAndVolumes extends ComputeBaseTest {

    private String yaml;
    private boolean hasNetwork;
    private boolean hasVolume;

    @Parameters
    public static List<Object[]> data() {
        Object[][] data = {
                { "docker.wordpress.1.yaml", false, false },
                { "docker.wordpress.2.yaml", false, false },
                { "docker.wordpress.network.yaml", true, false },
                { "docker.wordpress.volume.yaml", false, true },
                { "docker.wordpress.network.and.volume.yaml", true, true },
                { "composite.wordpress.yaml", false, false },
                { "composite.wordpress.network.yaml", true, false },
                { "composite.wordpress.volume.yaml", false, true },
                { "composite.wordpress.network.and.volume.yaml", true, true }
        };
        return Arrays.asList(data);
    }

    public CompositeDescriptionContentServiceWithNetworksAndVolumes(String filename,
            boolean hasNetwork, boolean hasVolume) {
        this.yaml = getContent(filename);
        this.hasNetwork = hasNetwork;
        this.hasVolume = hasVolume;
    }

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
        waitForServiceAvailability(CompositeDescriptionService.SELF_LINK);
    }

    @Test
    public void testImportYamlAndExportAsCompositeTemplate() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(yaml, hasNetwork, hasVolume, false));
        this.host.testWait();
    }

    @Test
    public void testImportYamlAndExportAsDockerCompose() throws Throwable {
        this.host.testStart(1);
        this.host.send(validateImportExportOperation(yaml, hasNetwork, hasVolume, true));
        this.host.testWait();
    }

    private Operation validateImportExportOperation(String yaml, boolean hasNetwork,
            boolean hasVolume, boolean exportAsDocker) {
        // import YAML to Container Description
        return Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .setBody(yaml)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        String location = o.getResponseHeader(Operation.LOCATION_HEADER);

                        try {
                            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
                            assertNotNull(location);
                        } catch (Throwable t) {
                            host.failIteration(t);
                            return;
                        }

                        // export Container Description as Docker Compose or Composite Template
                        Operation export = createExportOperation(location, exportAsDocker);
                        // get Container Description raw
                        Operation get = createGetDescriptionOperation(location);

                        OperationJoin.create(export, get).setCompletion((os, es) -> {
                            if (es != null && !es.isEmpty()) {
                                host.failIteration(es.values().iterator().next());
                                return;
                            }

                            try {
                                Operation op1 = os.get(export.getId());
                                assertEquals(Operation.STATUS_CODE_OK, op1.getStatusCode());
                                String content = op1.getBody(String.class);
                                assertNotNull(content);
                                if (exportAsDocker) {
                                    // export result as Docker Compose
                                    validateDockerYaml(content, hasNetwork, hasVolume);
                                } else {
                                    // export result as Composite Template
                                    validateCompositeYaml(content, hasNetwork, hasVolume);
                                }

                                Operation op2 = os.get(get.getId());
                                assertEquals(Operation.STATUS_CODE_OK, op2.getStatusCode());
                                CompositeDescription description = op2
                                        .getBody(CompositeDescription.class);
                                validateCompositeDescription(description, hasNetwork, hasVolume);
                            } catch (Throwable t) {
                                host.failIteration(t);
                                return;
                            }

                            host.completeIteration();
                        }).sendWith(host);
                    }
                });
    }

    private Operation createExportOperation(String selfLink, boolean docker) {
        URI uri = UriUtils.buildUri(host, CompositeDescriptionContentService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(uri,
                CompositeDescriptionContentService.SELF_LINK_PARAM_NAME, selfLink);
        if (docker) {
            uri = UriUtils.extendUriWithQuery(uri,
                    CompositeDescriptionContentService.FORMAT_PARAM_NAME,
                    CompositeDescriptionContentService.FORMAT_DOCKER_COMPOSE_TYPE);
        }
        return Operation.createGet(uri).setReferer(host.getReferer());
    }

    private Operation createGetDescriptionOperation(String selfLink) {
        return Operation.createGet(UriUtils.buildUri(host, selfLink)).setReferer(host.getReferer());
    }

    private static String getContent(String filename) {
        return FileUtil.getResourceAsString("/compose/" + filename, true);
    }

    private static void validateDockerYaml(String yaml, boolean hasNetwork, boolean hasVolume) {
        assertNotNull(yaml);
        assertTrue(yaml.contains("\nversion:"));
        assertTrue(yaml.contains("\nservices:"));
        assertFalse(yaml.contains("\nname:"));
        assertFalse(yaml.contains("\ncomponents:"));
        assertEquals(hasNetwork, yaml.contains("\nnetworks:"));
        assertEquals(hasVolume, yaml.contains("\nvolumes:"));
        validateContent(yaml, hasNetwork, hasVolume);
    }

    private static void validateCompositeYaml(String yaml, boolean hasNetwork, boolean hasVolume) {
        assertNotNull(yaml);
        assertTrue(yaml.contains("\nname:"));
        assertTrue(yaml.contains("\ncomponents:"));
        assertFalse(yaml.contains("\nversion:"));
        assertFalse(yaml.contains("\nservices:"));
        assertFalse(yaml.contains("\nnetworks:"));
        assertFalse(yaml.contains("\nvolumes:"));
        assertTrue(yaml.contains("type: \"Container.Docker\""));
        assertEquals(hasNetwork, yaml.contains("type: \"Network.Docker\""));
        assertEquals(hasVolume, yaml.contains("type: \"Volume.Docker\""));
        validateContent(yaml, hasNetwork, hasVolume);
    }

    private static void validateContent(String yaml, boolean hasNetwork, boolean hasVolume) {
        assertTrue(yaml.contains("db:"));
        assertTrue(yaml.contains("mysql:5.7"));
        assertTrue(yaml.contains("MYSQL_ROOT_PASSWORD"));
        assertTrue(yaml.contains("MYSQL_DATABASE"));
        assertTrue(yaml.contains("MYSQL_USER"));
        assertTrue(yaml.contains("MYSQL_PASSWORD"));
        assertTrue(yaml.contains("wordpress:"));
        assertTrue(yaml.contains("wordpress:latest"));
        assertTrue(yaml.contains("WORDPRESS_DB_HOST"));
        assertTrue(yaml.contains("WORDPRESS_DB_PASSWORD"));
        assertEquals(hasNetwork, yaml.contains("driver: \"front-driver-1\""));
        assertEquals(hasNetwork, yaml.contains("driver: \"back-driver-2\""));
        assertEquals(hasVolume, yaml.contains("driver: \"database-driver-1\""));
        assertEquals(hasVolume, yaml.contains("mountpoint: \"/data/local/data\""));
        assertEquals(hasVolume, yaml.contains("driver: \"phpconf-driver-2\""));
        assertEquals(hasVolume, yaml.contains("mountpoint: \"/etc/php5/conf.d\""));
    }

    private static void validateCompositeDescription(CompositeDescription description,
            boolean hasNetwork, boolean hasVolume) {
        assertNotNull(description);
        assertNotNull(description.name);
        assertTrue(description.name.startsWith("Docker Compose"));
        assertNotNull(description.descriptionLinks);

        int totalDescriptions = 2;
        if (hasNetwork) {
            totalDescriptions += 2;
        }
        if (hasVolume) {
            totalDescriptions += 2;
        }

        assertEquals(totalDescriptions, description.descriptionLinks.size());
        assertNull(description.tenantLinks); // default tenant / standalone mode
    }
}
