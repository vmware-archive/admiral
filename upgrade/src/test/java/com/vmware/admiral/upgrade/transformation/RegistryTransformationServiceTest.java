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

package com.vmware.admiral.upgrade.transformation;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.upgrade.UpgradeBaseTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class RegistryTransformationServiceTest extends UpgradeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryService.FACTORY_LINK);
        waitForServiceAvailability(RegistryService.DEFAULT_INSTANCE_LINK);
        waitForServiceAvailability(ProjectService.DEFAULT_PROJECT_LINK);
        waitForServiceAvailability(RegistryTransformationService.SELF_LINK);
    }

    @Test
    public void testDefaultProjectDefaultRegistry() throws Throwable {
        List<String> registriesBeforeTransformation = getDocumentLinksOfType(RegistryState.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, RegistryTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> registriesAfterTransformation = getDocumentLinksOfType(RegistryState.class);
        Assert.assertTrue(
                registriesBeforeTransformation.size() + 1 == registriesAfterTransformation.size());
        registriesAfterTransformation.removeAll(registriesBeforeTransformation);

        RegistryState clonedRegistry = getDocument(RegistryState.class,
                registriesAfterTransformation.get(0));
        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);

        Assert.assertTrue(clonedRegistry.tenantLinks.size() == 1);
        Assert.assertTrue(clonedRegistry.tenantLinks.contains(defaultProject.documentSelfLink));
    }

    @Test
    public void testMultipleProjectsDefaultRegistries() throws Throwable {
        ProjectState project = createProject(PROJECT_NAME_TEST_PROJECT_1);
        List<String> registriesBeforeTransformation = getDocumentLinksOfType(RegistryState.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, RegistryTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> registriesAfterTransformation = getDocumentLinksOfType(RegistryState.class);
        Assert.assertTrue(
                registriesBeforeTransformation.size() + 2 == registriesAfterTransformation.size());
        registriesAfterTransformation.removeAll(registriesBeforeTransformation);

        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);
        for (String selfLink : registriesAfterTransformation) {
            RegistryState document = getDocument(RegistryState.class, selfLink);
            Assert.assertTrue(document.tenantLinks.size() == 1);
            Assert.assertTrue(document.tenantLinks.contains(defaultProject.documentSelfLink)
                    || document.tenantLinks.contains(project.documentSelfLink));
        }
    }

    @Test
    public void testMultipleProjectsMultipleRegistries() throws Throwable {
        ProjectState project = createProject(PROJECT_NAME_TEST_PROJECT_1);
        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);

        RegistryState registryState = new RegistryState();
        registryState.documentSelfLink = "Registry1";
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState.address = "Test";
        registryState = doPost(registryState, RegistryService.FACTORY_LINK);

        List<String> registriesBeforeTransformation = getDocumentLinksOfType(RegistryState.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, RegistryTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> registriesAfterTransformation = getDocumentLinksOfType(RegistryState.class);
        // Every registry should be cloned per project. In total the count should be 6. 2 initial
        // and 4 new
        Assert.assertTrue(
                registriesBeforeTransformation.size() + 4 == registriesAfterTransformation.size());
        registriesAfterTransformation.removeAll(registriesBeforeTransformation);

        for (String selfLink : registriesAfterTransformation) {
            RegistryState document = getDocument(RegistryState.class, selfLink);
            Assert.assertTrue(document.tenantLinks.size() == 1);
            Assert.assertTrue(document.tenantLinks.contains(defaultProject.documentSelfLink)
                    || document.tenantLinks.contains(project.documentSelfLink));
        }
    }
}
