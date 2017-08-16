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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.upgrade.UpgradeBaseTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class CompositeDescriptionTransformationServiceTest extends UpgradeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(CompositeDescriptionService.SELF_LINK);
        waitForServiceAvailability(ProjectService.DEFAULT_PROJECT_LINK);
        waitForServiceAvailability(CompositeDescriptionTransformationService.SELF_LINK);
    }

    @Test
    public void testNoTempletesDefaultProject() throws Throwable {
        List<String> descriptionsBeforeTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        Assert.assertTrue(descriptionsBeforeTransformation.size() == 0);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeDescriptionTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> descriptionsAfterTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        Assert.assertTrue(
                descriptionsBeforeTransformation.size() == descriptionsAfterTransformation
                        .size());
    }

    @Test
    public void testSingleTempleteDefaultProject() throws Throwable {
        createCompositeDescription("Description1");
        List<String> descriptionsBeforeTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeDescriptionTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> descriptionsAfterTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        Assert.assertTrue(
                descriptionsBeforeTransformation.size() + 1 == descriptionsAfterTransformation
                        .size());
        descriptionsAfterTransformation.removeAll(descriptionsBeforeTransformation);

        CompositeDescription clonedDescription = getDocument(CompositeDescription.class,
                descriptionsAfterTransformation.get(0));
        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);

        Assert.assertTrue(clonedDescription.tenantLinks.size() == 1);
        Assert.assertTrue(clonedDescription.tenantLinks.contains(defaultProject.documentSelfLink));
    }

    @Test
    public void testMultipleProjectsSingleTemplate() throws Throwable {
        createCompositeDescription("Description1");
        ProjectState project = createProject(PROJECT_NAME_TEST_PROJECT_1);
        List<String> descriptionsBeforeTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeDescriptionTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> descriptionsAfterTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        Assert.assertTrue(
                descriptionsBeforeTransformation.size() + 2 == descriptionsAfterTransformation
                        .size());
        descriptionsAfterTransformation.removeAll(descriptionsBeforeTransformation);

        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);
        for (String selfLink : descriptionsAfterTransformation) {
            CompositeDescription document = getDocument(CompositeDescription.class, selfLink);
            Assert.assertTrue(document.tenantLinks.size() == 1);
            Assert.assertTrue(document.tenantLinks.contains(defaultProject.documentSelfLink)
                    || document.tenantLinks.contains(project.documentSelfLink));
        }
    }

    @Test
    public void testMultipleProjectsMultipleTemplates() throws Throwable {
        createCompositeDescription("Description1");
        createCompositeDescription("Description1");
        ProjectState project = createProject(PROJECT_NAME_TEST_PROJECT_1);
        ProjectState defaultProject = getDocument(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);

        List<String> descriptionsBeforeTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeDescriptionTransformationService.SELF_LINK), false,
                Service.Action.POST);
        List<String> compositeDescriptionsAfterTransformation = getDocumentLinksOfType(
                CompositeDescription.class);
        // Every template should be cloned per project. In total the count should be 6. 2 initial
        // and 4 new
        Assert.assertTrue(
                descriptionsBeforeTransformation.size()
                        + 4 == compositeDescriptionsAfterTransformation.size());
        compositeDescriptionsAfterTransformation.removeAll(descriptionsBeforeTransformation);

        for (String selfLink : compositeDescriptionsAfterTransformation) {
            CompositeDescription document = getDocument(CompositeDescription.class, selfLink);
            Assert.assertTrue(document.tenantLinks.size() == 1);
            Assert.assertTrue(document.tenantLinks.contains(defaultProject.documentSelfLink)
                    || document.tenantLinks.contains(project.documentSelfLink));
        }
    }

    private CompositeDescription createCompositeDescription(String name) throws Throwable {
        ContainerDescription firstContainer = new ContainerDescription();
        firstContainer.name = "testContainer";
        firstContainer.image = "registry.hub.docker.com/nginx";
        firstContainer._cluster = 1;
        firstContainer.maximumRetryCount = 1;
        firstContainer.privileged = true;
        firstContainer.affinity = new String[] { "cond1", "cond2" };
        firstContainer.customProperties = new HashMap<String, String>();
        firstContainer.customProperties.put("key1", "value1");
        firstContainer.customProperties.put("key2", "value2");

        ContainerDescription createdFirstContainer = doPost(firstContainer,
                ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription secondContainer = new ContainerDescription();
        secondContainer.name = "testContainer2";
        secondContainer.image = "registry.hub.docker.com/kitematic/hello-world-nginx";

        ContainerDescription createdSecondContainer = doPost(secondContainer,
                ContainerDescriptionService.FACTORY_LINK);

        CompositeDescription composite = new CompositeDescription();
        composite.name = "name";
        composite.customProperties = new HashMap<String, String>();
        composite.customProperties.put("key1", "value1");
        composite.customProperties.put("key2", "value2");
        composite.descriptionLinks = new ArrayList<String>();
        composite.descriptionLinks.add(createdFirstContainer.documentSelfLink);
        composite.descriptionLinks.add(createdSecondContainer.documentSelfLink);

        return doPost(composite, CompositeDescriptionService.SELF_LINK);
    }

}
