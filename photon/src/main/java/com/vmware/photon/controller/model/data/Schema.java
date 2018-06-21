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

package com.vmware.photon.controller.model.data;

import java.util.Map;

/**
 * Defines a data schema with name, description and map of field definitions
 */
public class Schema {

    /**
     * short schema name
     */
    public String name;

    /**
     * schema description
     */
    public String description;

    /**
     * schema fields
     */
    public Map<String, SchemaField> fields;
}
