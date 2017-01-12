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
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.TemplateSearchService.Response;
import com.vmware.admiral.compute.container.TemplateSpec.TemplateType;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

/**
 * Test template search service
 */
public class TemplateSearchServiceTest extends ComputeBaseTest {
    private static final String TEST_COMPOSITE_DESC_NAME = "wordPressWithMySql";
    private static final String TEST_COMPOSITE_DESC_NAME_CLONED = "wordPressWithMySqlCloned";
    private static final String TEST_CONTAINER_DESC_NAME = "mysql";
    private static final String TEST_IMAGE_NAME = "library/mysql-5";

    // match both the CompositeDesc and the ContainerDesc name
    private static final String TEST_COMMON = "*y*";

    private String containerDescSelfLink;
    private String compositeDescSelfLink;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        createContainerDescription(false);

        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        createCompositeDescription(null, false);

        waitForServiceAvailability(TemplateSearchService.SELF_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        // we want tear down operations to succeed even if the test failed
        clearFailure();

        verifyOperation(Operation.createDelete(UriUtils.buildUri(host, containerDescSelfLink))
                .setBody(Operation.EMPTY_JSON_BODY));

        verifyOperation(Operation.createDelete(UriUtils.buildUri(host, compositeDescSelfLink))
                .setBody(Operation.EMPTY_JSON_BODY));
    }

    @Test
    public void testCompositeDescSearch() throws Throwable {
        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME);
    }

    @Test
    public void testCompositeDescAndUnexistingGroupSearch() throws Throwable {
        String tenantLink = "/tenants/otherGroup";
        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, true, false, false, tenantLink, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 0, response.results.size());
        });
    }

    @Test
    public void testCompositeDescAndGlobalGroupSearch() throws Throwable {
        String tenantLink = "/tenants/someGroup";
        createCompositeDescription(tenantLink, false);
        // do a global search, verify that application from the tenant is returned
        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, true, false, false, tenantLink, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 1, response.results.size());
            for (TemplateSpec template : response.results) {
                assertEquals(Collections.singletonList(tenantLink), template.tenantLinks);
            }
        });
    }

    /**
     * VSYM-307: Searching for template returns wrong results
     */
    @Test
    public void testCompositeDescAndUnexistingNameSearch() throws Throwable {
        String tenantLink = "/tenants/randomGroup";

        // delete old composite description, and create a new one for a given group
        verifyOperation(Operation.createDelete(UriUtils.buildUri(host, compositeDescSelfLink))
                .setBody(Operation.EMPTY_JSON_BODY));
        createCompositeDescription(tenantLink, false);

        verifyTemplateSearchResult("gibberish", true, false, false, tenantLink, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 0, response.results.size());
        });
    }

    @Test
    public void testCompositeDescAndGroupSearch() throws Throwable {
        String tenantLink = "/tenants/someGroup";

        // delete old composite description, and create a new one for a given group
        verifyOperation(Operation.createDelete(UriUtils.buildUri(host, compositeDescSelfLink))
                .setBody(Operation.EMPTY_JSON_BODY));
        createCompositeDescription(tenantLink, false);

        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, true, false, false, tenantLink, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 1, response.results.size());
            TemplateSpec templateSpec = response.results.iterator().next();
            assertEquals("results[0].tenantLinks", Collections.singletonList(tenantLink),
                    templateSpec.tenantLinks);
        });
    }

    @Test
    public void testCompositeDescAndOnlyParents() throws Throwable {
        createCompositeDescription(null, true);

        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, true, true, false, null, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 1, response.results.size());
            TemplateSpec templateSpec = response.results.iterator().next();
            assertEquals("results[0].name", TEST_COMPOSITE_DESC_NAME, templateSpec.name);
        });
    }

    @Test
    public void testContainedContainerDescNameTest() throws Throwable {
        verifyTemplateSearchResult(TEST_CONTAINER_DESC_NAME);
    }

    @Test
    public void testContainedContainerDescImageTest() throws Throwable {
        verifyTemplateSearchResult(TEST_IMAGE_NAME);
    }

    /**
     * Verify that when the same CompositeDescription is matched both by its own name and by one of
     * its contained ContainerDescription's name it is only returned once
     *
     * @throws Throwable
     */
    @Test
    public void testNoDuplicateResultWhenBothMatch() throws Throwable {
        verifyTemplateSearchResult(TEST_COMMON);
    }

    @Test
    public void testImagesOnlyDoesntReturnCompositeDesc() throws Throwable {
        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, false, false, true, null, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 0, response.results.size());
        });
    }

    @Test(expected = LocalizableValidationException.class)
    public void testImagesOnlyAndTemplatesOnlyNotAllowed() throws Throwable {
        verifyTemplateSearchResult(TEST_COMPOSITE_DESC_NAME, true, false, true, null, (o) -> {
        });
    }

    private void verifyTemplateSearchResult(String query) throws Throwable {
        verifyTemplateSearchResult(query, true, false, false, null, (o) -> {
            Response response = o.getBody(Response.class);
            assertNotNull("results", response.results);
            assertEquals("results.size", 1, response.results.size());
            TemplateSpec templateSpec = response.results.iterator().next();
            assertEquals("results[0].templateType", TemplateType.COMPOSITE_DESCRIPTION,
                    templateSpec.templateType);
        });
    }

    private void verifyTemplateSearchResult(String query, boolean templatesOnly,
            boolean templatesParentOnly, boolean imagesOnly, String group,
            Consumer<Operation> verification) throws Throwable {

        URI templateSearchUri = UriUtils.buildUri(host, TemplateSearchService.SELF_LINK);

        final List<String> keyValues = new ArrayList<String>(Arrays.asList(
                TemplateSearchService.TEMPLATES_ONLY_PARAM, String.valueOf(templatesOnly),
                TemplateSearchService.TEMPLATES_PARENT_ONLY_PARAM, String.valueOf(templatesParentOnly),
                TemplateSearchService.IMAGES_ONLY_PARAM, String.valueOf(imagesOnly),
                TemplateSearchService.QUERY_PARAM, query));

        if (group != null) {
            keyValues.add(TemplateSearchService.GROUP_PARAM);
            keyValues.add(group);
        }

        templateSearchUri = UriUtils.extendUriWithQuery(templateSearchUri,
                keyValues.toArray(new String[keyValues.size()]));

        verifyOperation(Operation.createGet(templateSearchUri), verification);

    }

    private void createContainerDescription(boolean cloned) throws Throwable {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = TEST_CONTAINER_DESC_NAME;
        containerDesc.name = TEST_CONTAINER_DESC_NAME;
        containerDesc.image = TEST_IMAGE_NAME;

        if (cloned) {
            containerDesc.parentDescriptionLink = TEST_CONTAINER_DESC_NAME;
        }

        verifyOperation(OperationUtil.createForcedPost(
                UriUtils.buildFactoryUri(host, ContainerDescriptionService.class))
                .setBody(containerDesc),
                (o) -> {
                    ContainerDescription cd = o.getBody(ContainerDescription.class);
                    containerDescSelfLink = cd.documentSelfLink;
                });

    }

    private void createCompositeDescription(String tenantLink, boolean cloned) throws Throwable {
        CompositeDescription compositeDesc = new CompositeDescription();
        compositeDesc.name = TEST_COMPOSITE_DESC_NAME;

        if (cloned) {
            compositeDesc.parentDescriptionLink = TEST_COMPOSITE_DESC_NAME;
            compositeDesc.documentSelfLink =  TEST_COMPOSITE_DESC_NAME_CLONED;
        }

        if (tenantLink != null) {
            compositeDesc.tenantLinks = Collections.singletonList(tenantLink);
        }

        compositeDesc.descriptionLinks = Collections.singletonList(containerDescSelfLink);
        System.out.println(getFactoryUrl(CompositeDescriptionFactoryService.class).toString());
        verifyOperation(OperationUtil.createForcedPost(
                getFactoryUrl(CompositeDescriptionFactoryService.class))
                .setBody(compositeDesc),
                (o) -> {
                    CompositeDescription cd = o.getBody(CompositeDescription.class);
                    compositeDescSelfLink = cd.documentSelfLink;
                });
    }

}
