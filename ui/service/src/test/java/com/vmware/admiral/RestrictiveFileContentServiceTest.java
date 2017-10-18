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

package com.vmware.admiral;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.URL;

import org.junit.Test;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class RestrictiveFileContentServiceTest {

    @Test
    public void testGetContentWhenNotEmbeddedWithXFrameOptions() throws Exception {

        URL resource = this.getClass()
                .getResource("/ui/com/vmware/admiral/UiService/container-icons/vmware/admiral.png");

        RestrictiveFileContentService service = new RestrictiveFileContentService(
                new File(resource.toURI()));
        service.setSelfLink("/container-icons/vmware/admiral.png");
        VerificationHost vh = new VerificationHost() {
            @Override
            public void sendRequest(Operation op) {
                op.fail(Operation.STATUS_CODE_NOT_FOUND);
            }
        };
        service.setHost(vh);

        // see comment in RestrictiveFileContentService's isEmbedded assignment
        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = Boolean.toString(false);
        ConfigurationUtil.initialize(config);

        Operation get = new Operation()
                .setUri(UriUtils.buildUri("/container-icons/vmware/admiral.png"));

        service.handleGet(get);

        assertEquals(Operation.STATUS_CODE_OK, get.getStatusCode());
        assertEquals("SAMEORIGIN",
                get.getResponseHeader(ConfigurationUtil.UI_FRAME_OPTIONS_HEADER));
    }

    @Test
    public void testGetContentWhenEmbeddedWithoutXFrameOptions() throws Exception {

        URL resource = this.getClass()
                .getResource("/ui/com/vmware/admiral/UiService/container-icons/vmware/admiral.png");

        RestrictiveFileContentService service = new RestrictiveFileContentService(
                new File(resource.toURI()));
        service.setSelfLink("/container-icons/vmware/admiral.png");
        VerificationHost vh = new VerificationHost() {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/config/props/embedded")) {
                    ConfigurationState body = new ConfigurationState();
                    body.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
                    body.value = Boolean.toString(true);
                    op.setBody(body);
                    op.complete();
                } else {
                    op.fail(Operation.STATUS_CODE_NOT_FOUND);
                }
            }
        };
        service.setHost(vh);

        // see comment in RestrictiveFileContentService
        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = Boolean.toString(true);
        ConfigurationUtil.initialize(config);

        // regular request

        Operation get = new Operation()
                .setUri(UriUtils.buildUri("/container-icons/vmware/admiral.png"));

        service.handleGet(get);

        assertEquals(Operation.STATUS_CODE_NOT_FOUND, get.getStatusCode());
        assertNull(get.getResponseHeader(ConfigurationUtil.UI_FRAME_OPTIONS_HEADER));

        // regular request with proxy forward header

        Operation getWithProxy = new Operation()
                .setUri(UriUtils.buildUri("/container-icons/vmware/admiral.png"));
        getWithProxy.addRequestHeader(ConfigurationUtil.UI_PROXY_FORWARD_HEADER, "whatever");

        service.handleGet(getWithProxy);

        assertEquals(Operation.STATUS_CODE_OK, getWithProxy.getStatusCode());
        assertNull(getWithProxy.getResponseHeader(ConfigurationUtil.UI_FRAME_OPTIONS_HEADER));
    }

}
