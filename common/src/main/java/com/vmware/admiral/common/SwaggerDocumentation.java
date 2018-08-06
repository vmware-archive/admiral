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

    }

}