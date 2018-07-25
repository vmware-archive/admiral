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

package com.vmware.admiral.host.swagger;

import java.io.IOException;
import java.net.URL;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.UiContentService;

public class SwaggerDocumentationService extends UiContentService {

    public static final String SELF_LINK = "/api";

    public SwaggerDocumentationService() {
        this.toggleOption(Service.ServiceOption.HTML_USER_INTERFACE, true);
        this.toggleOption(Service.ServiceOption.CONCURRENT_GET_HANDLING, true);
    }

    @Override
    public void handleGet(Operation get) {
        try {
            URL swaggerJsonFile = Resources.getResource("swagger-ui.json");
            get.setBody(Resources.toString(swaggerJsonFile, Charsets.UTF_8)).complete();
        } catch (IOException e) {
            get.fail(e);
        }
    }
}
