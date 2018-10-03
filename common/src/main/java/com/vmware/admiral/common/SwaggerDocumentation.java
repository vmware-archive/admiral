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

/**
 * Interface holding all needed constants for documenting the project's services with Swagger.
 */
public interface SwaggerDocumentation {

    public static final String BASE_PATH = "/";
    public static final String INSTANCE_PATH = "/{id}";
    public static final String LINE_BREAK = "<br/>";

    public static interface InfoConstants {

        public static final String TITLE = "Admiral API";

        public static final String CONTACT_NAME = "VMware Admiral";
        public static final String CONTACT_URL = "https://github.com/vmware/admiral";

        public static final String LICENSE_NAME = "Apache 2 License";
        public static final String LICENSE_URL = "https://github.com/vmware/admiral/blob/master/LICENSE";

        public static final String DESCRIPTION =
                "Admiral™ is a highly scalable and very lightweight Container Management " +
                "platform for deploying and managing container\nbased applications. It is designed to have a small " +
                "footprint and boot extremely quickly. Admiral™ is intended to\nprovide automated deployment and " +
                "lifecycle management of containers.\n\nThis container management solution can help reduce complexity " +
                "and achieve advantages including simplified and automated\napplication delivery, optimized resource " +
                "utilization along with business governance and applying business policies and\noverall data center " +
                "integration.";
    }

    public static interface DataTypes {
        public static final String DATA_TYPE_STRING = "string";
        public static final String DATA_TYPE_INTEGER = "integer";
        public static final String DATA_TYPE_DECIMAL = "decimal";
        public static final String DATA_TYPE_BOOLEAN = "boolean";
        public static final String DATA_TYPE_OBJECT = "object";
    }

    public static interface ParamTypes {
        public static final String PARAM_TYPE_PATH = "path";
        public static final String PARAM_TYPE_QUERY = "query";
        public static final String PARAM_TYPE_BODY = "body";
        public static final String PARAM_TYPE_HEADER = "header";
        public static final String PARAM_TYPE_FORM = "form";
    }

    public static interface Tags {

        public static final String FAVORITE_IMAGES_TAG = "Favorite Images";

        public static final String PKS_CLUSTER_CONFIG_TAG = "PKS Cluster Configuration";
        public static final String PKS_CLUSTER_LIST_TAG = "PKS Cluster List";
        public static final String PKS_PLAN_LIST_TAG = "PKS Plan List";
        public static final String PKS_CLUSTER_CRUD_TAG = "PKS Cluster Operations";

        public static final String EVENT_LOGS = "Event Logs";

    }

}