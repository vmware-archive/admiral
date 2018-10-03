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

import io.swagger.models.Info;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.util.Json;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.SwaggerDocumentation;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.UiContentService;

public class SwaggerDocumentationService extends UiContentService {

    public static final String SELF_LINK = ManagementUriParts.SWAGGER_DOCUMENTATION_LINK;

    private Swagger swagger = new Swagger();
    private Info info;
    private String[] includePackages;
    private Scheme[] schemes;


    public SwaggerDocumentationService() {
        this.toggleOption(ServiceOption.HTML_USER_INTERFACE, true);
        this.toggleOption(ServiceOption.CONCURRENT_GET_HANDLING, true);
    }

    public SwaggerDocumentationService setInfo(Info info) {
        this.info = info;
        return this;
    }

    public SwaggerDocumentationService setIncludePackages(String... includePackages) {
        this.includePackages = includePackages;
        return this;
    }

    public SwaggerDocumentationService setSchemes(Scheme... schemes) {
        this.schemes = schemes;
        return this;
    }

    @Override
    public void handleGet(Operation get) {
        this.swagger = SwaggerDocumentationAssembler
                .create()
                .setHost(get.getReferer().getAuthority())
                .setBasePath(SwaggerDocumentation.BASE_PATH)
                .setInfo(this.info)
                .setIncludePackages(this.includePackages)
                .setSchemes(this.schemes)
                .build();

        get.setBody(Json.pretty(swagger)).complete();
    }
}
