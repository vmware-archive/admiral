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

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Helper class to build schemas with a dsl-like syntax
 */
public class SchemaBuilder {
    private Schema schema;
    private HashMap<String, SchemaFieldBuilder> fields;
    private SchemaFieldBuilder parent;

    public SchemaBuilder() {
        this.schema = new Schema();
        this.fields = new HashMap<>();
    }

    protected SchemaBuilder(SchemaFieldBuilder fieldBuilder) {
        this();
        this.parent = fieldBuilder;
    }

    public SchemaFieldBuilder addField(String fieldName) {
        SchemaFieldBuilder fieldBuilder = new SchemaFieldBuilder(this);
        this.fields.put(fieldName, fieldBuilder);
        return fieldBuilder;
    }

    public SchemaBuilder withName(String name) {
        this.schema.name = name;
        return this;
    }

    public SchemaBuilder withDescription(String description) {
        this.schema.description = description;
        return this;
    }

    public Schema build() {
        this.schema.fields = this.fields
                .entrySet().stream()
                .collect(Collectors
                        .toMap(entry -> entry.getKey(), entry -> entry.getValue().build()));
        return this.schema;
    }

    public SchemaFieldBuilder done() {
        return this.parent;
    }
}