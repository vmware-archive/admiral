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

package com.vmware.admiral.compute.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.host.interceptor.ProjectInterceptor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests that {@link CompositeDescription}s imported through the
 * {@link CompositeDescriptionContentService} have a project tenant link set if and only if we are
 * not in embedded mode.
 */
@RunWith(Parameterized.class)
public class TemplateImportTenantLinksTest extends ComputeBaseTest {

    private static final String TEMPLATE_FILENAME = "test_blueprint.yaml";
    private static final String TEST_PROJECT_HEADER = ProjectFactoryService.SELF_LINK
            + "/test-project";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { TEMPLATE_FILENAME, false },
                { TEMPLATE_FILENAME, true }
        });
    }

    private final boolean embeddedMode;
    private String templateFileName;
    private String template;

    public TemplateImportTenantLinksTest(String templateFileName, boolean embeddedMode)
            throws Throwable {
        this.templateFileName = templateFileName;
        this.embeddedMode = embeddedMode;
        toggleEmbeddedMode(embeddedMode);
    }

    @Before
    public void setUp() throws Throwable {
        this.template = CommonTestStateFactory.getFileContent(templateFileName);
        waitForServiceAvailability(CompositeDescriptionContentService.SELF_LINK);
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        super.registerInterceptors(registry);
        ProjectInterceptor.register(registry);
    }

    @Test
    public void testImportCompositeDescriptionAndVerifyTenantLinks() throws Throwable {
        CompositeDescription template = createTemplate();
        verifyTemplateTenantLinks(template);
    }

    private void verifyTemplateTenantLinks(CompositeDescription template) throws Throwable {
        String message = String.format("Template tenant links [%s]  %s contain %s",
                template.tenantLinks == null ? null : String.join(", ", template.tenantLinks),
                this.embeddedMode ? "must not" : "must",
                TEST_PROJECT_HEADER);

        // project tenant link must be set if and only if we are not in embedded mode
        assertEquals(message, !this.embeddedMode,
                template.tenantLinks != null && template.tenantLinks.contains(TEST_PROJECT_HEADER));
    }

    private CompositeDescription createTemplate() throws Throwable {
        Operation createOp = Operation.createPost(UriUtils.buildUri(host,
                CompositeDescriptionContentService.SELF_LINK))
                .addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, TEST_PROJECT_HEADER)
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .forceRemote()
                .setBody(template);

        AtomicReference<String> location = new AtomicReference<>();
        verifyOperation(createOp, (o) -> {
            assertEquals("status code", Operation.STATUS_CODE_OK, o.getStatusCode());

            location.set(o.getResponseHeader(Operation.LOCATION_HEADER));
            assertNotNull("location header", location);
        });

        return getDocument(CompositeDescription.class, location.get());
    }
}
