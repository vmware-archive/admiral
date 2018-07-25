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

package com.vmware.admiral.common;

import javax.ws.rs.ext.Provider;

import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

/**
 * Interface holding all needed constants for documenting the project's services with Swagger.
 */
@Provider
@SwaggerDefinition(
        info = @Info(
                title = "Admiral Open API",
                version = "v1",
                description = SwaggerDocumentation.ADMIRAL_DESCRIPTION,
                contact = @Contact(
                        name = "VMware Admiral Team",
                        url = "https://github.com/vmware/admiral"),
                license = @License(
                        name = "Apache 2 license",
                        url = "https://github.com/vmware/admiral/blob/master/LICENSE")),
        schemes = {SwaggerDefinition.Scheme.HTTP, SwaggerDefinition.Scheme.HTTPS})
public interface SwaggerDocumentation {

    public static final String ADMIRAL_DESCRIPTION = "Admiral™ is a highly scalable and very lightweight Container " +
            "Management platform for deploying and managing container based applications. It is designed to have a " +
            "small footprint and boot extremely quickly. Admiral™ is intended to provide automated deployment and " +
            "lifecycle management of containers.\n\nThis container management solution can help reduce complexity " +
            "and achieve advantages including simplified and automated application delivery, optimized resource " +
            "utilization along with business governance and applying business policies and overall data center " +
            "integration.\n\nAdmiral is a service written in Java and based on VMware's Xenon framework. This service " +
            "enables the users to:\n\nmanage Docker hosts, where containers will be deployed\nmanage Policies " +
            "(together with Resource Pools, Deployment Policies, etc.), to establish the preferences about what host(s) " +
            "a container deployment will actually use\nmanage Templates (including one or more container images) and " +
            "Docker Registries\nmanage Containers and Applications\nmanage other common and required entities like " +
            "credentials, certificates, etc.";

    public static final String BASE_PATH = "/";
    public static final String INSTANCE_PATH = "/{id}";
    public static final String LINE_BREAK = "<br/>";

    public static final String PARAM_TYPE_PATH = "path";
    public static final String PARAM_TYPE_QUERY = "query";
    public static final String PARAM_TYPE_BODY = "body";
    public static final String PARAM_TYPE_HEADER = "header";
    public static final String PARAM_TYPE_FORM = "form";

    public static interface Tags {

        public static final String FAVORITE_IMAGES_TAG = "Favorite Images";

        public static final String PKS_CLUSTER_CONFIG_TAG = "PKS Cluster Configuration";
        public static final String PKS_CLUSTER_LIST_TAG = "PKS Cluster List";
        public static final String PKS_PLAN_LIST_TAG = "PKS Plan List";
        public static final String PKS_CLUSTER_CRUD_TAG = "PKS Cluster Operations";

    }

}