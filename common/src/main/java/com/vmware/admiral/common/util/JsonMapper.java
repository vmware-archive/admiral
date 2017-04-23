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

package com.vmware.admiral.common.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import com.vmware.xenon.common.ServiceDocument;

/**
 * Jackson JSON mapping functions
 */
public class JsonMapper {
    public static final String SERVICE_DOCUMENT_FILTER = "serviceDocumentFilter";
    private static final ObjectMapper objectMapper = createObjectMapper();
    private static final FilterProvider filters = new SimpleFilterProvider().addFilter(
            SERVICE_DOCUMENT_FILTER, createBuiltinFieldFilter());
    private static final ObjectWriter objectWriter = objectMapper.writer(filters);

    public static ObjectMapper objectMapper() {
        return objectMapper;
    }

    public static ObjectWriter objectWriter() {
        return objectWriter;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        objectMapper.setFilterProvider(filters);
        return objectMapper;
    }

    public static <T> String toJSON(T obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    public static <T> T fromJSON(String json, Class<T> clazz) throws IOException {
        return objectMapper.readValue(json, clazz);
    }

    public static PropertyFilter createBuiltinFieldFilter() {
        return SimpleBeanPropertyFilter.serializeAllExcept(getBuiltinFieldNames());
    }

    public static Set<String> getBuiltinFieldNames() {
        Field[] fields = ServiceDocument.class.getFields();
        return Arrays.stream(fields)
                .map(Field::getName)
                .filter(ServiceDocument::isBuiltInDocumentField)
                .collect(Collectors.toSet());
    }

}
