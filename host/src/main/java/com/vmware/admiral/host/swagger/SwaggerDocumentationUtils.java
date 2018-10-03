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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.Path;

import io.swagger.annotations.*;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;

import com.vmware.admiral.common.SwaggerDocumentation;

/**
 * Utility class which extracts Swagger annotation values to build
 * {@link Swagger} objects for documentation purposes.
 */
public class SwaggerDocumentationUtils {

    private SwaggerDocumentationUtils() {}

    /**
     * Return a list of {@link Tag} objects created from the {@link Api#tags()} property.
     *
     * @param clazz The annotated service class
     * @return A list of {@link Tag} objects
     */
    public static List<Tag> getApiTagsAsList(Class<?> clazz) {
        return Arrays.asList(clazz.getAnnotation(Api.class).tags())
                .stream()
                .map(tag -> new Tag().name(tag))
                .collect(Collectors.toList());

    }

    /**
     * Return a stream of all class methods annotated with {@link ApiOperation}. These
     * are the methods responsible for handling REST calls to the service described in
     * {@code clazz}.
     *
     * @param clazz The service class
     * @return A stream of {@link Method} objects which handle REST calls
     */
    public static Stream<Method> getApiMethods(Class<?> clazz) {
        return Arrays.asList(clazz.getMethods())
                .stream()
                .filter(method -> method.isAnnotationPresent(ApiOperation.class));
    }

    /**
     * Build the URI for the REST operation executed by the {@code method} to be
     * used in {@link Swagger#paths}. If the method has no {@link Path} annotation
     * the {@link SwaggerDocumentation#BASE_PATH} is used.
     *
     * @param method The method which executes a REST operation.
     * @return The URI of the method.
     */
    public static String methodPath(Method method) {
        return method.getAnnotation(Path.class) == null ?
                SwaggerDocumentation.BASE_PATH :
                method.getAnnotation(Path.class).value();
    }

    /**
     * Build a {@link BodyParameter} Swagger object defined by {@link ApiImplicitParam}
     * to use for documentation.
     *
     * @param apiParam The annotation which documents the parameter.
     * @return The {@link BodyParameter} object to be used in {@link Swagger}
     */
    public static BodyParameter bodyParameter(ApiImplicitParam apiParam) {
        BodyParameter bodyParameter = new BodyParameter()
                .name(apiParam.name())
                .description(apiParam.value())
                .schema(new RefModel().asDefault(apiParam.dataTypeClass().getSimpleName()));
        bodyParameter.setRequired(apiParam.required());
        return bodyParameter;
    }

    /**
     * Build a {@link FormParameter} Swagger object defined by {@link ApiImplicitParam}
     * to use for documentation.
     *
     * @param apiParam The annotation which documents the parameter.
     * @return The {@link FormParameter} object to be used in {@link Swagger}
     */
    public static FormParameter formParameter(ApiImplicitParam apiParam) {
        return new FormParameter()
                .name(apiParam.name())
                .description(apiParam.value())
                .required(apiParam.required())
                .type(apiParam.dataType());
    }

    /**
     * Build a {@link HeaderParameter} Swagger object defined by {@link ApiImplicitParam}
     * to use for documentation.
     *
     * @param apiParam The annotation which documents the parameter.
     * @return The {@link HeaderParameter} object to be used in {@link Swagger}
     */
    public static HeaderParameter headerParameter(ApiImplicitParam apiParam) {
        return new HeaderParameter()
                .name(apiParam.name())
                .description(apiParam.value())
                .required(apiParam.required())
                .type(apiParam.dataType());
    }

    /**
     * Build a {@link PathParameter} Swagger object defined by {@link ApiImplicitParam}
     * to use for documentation.
     *
     * @param apiParam The annotation which documents the parameter.
     * @return The {@link PathParameter} object to be used in {@link Swagger}
     */
    public static PathParameter pathParameter(ApiImplicitParam apiParam) {
        return new PathParameter()
                .name(apiParam.name())
                .description(apiParam.value())
                .required(apiParam.required())
                .type(apiParam.dataType());
    }

    /**
     * Build a {@link QueryParameter} Swagger object defined by {@link ApiImplicitParam}
     * to use for documentation.
     *
     * @param apiParam The annotation which documents the parameter.
     * @return The {@link QueryParameter} object to be used in {@link Swagger}
     */
    public static QueryParameter queryParameter(ApiImplicitParam apiParam) {
        return new QueryParameter()
                .name(apiParam.name())
                .description(apiParam.value())
                .required(apiParam.required())
                .type(apiParam.dataType());
    }

    /**
     * Build a {@link Model} Swagger object defined by {@link ApiModel} to use for
     * documentation. The model is described by the {@code clazz} object and contains
     * fields, of which only the ones annotated with {@link ApiModelProperty} are
     * going to appear in the documentation.
     *
     * @param clazz The class defining the model.
     * @return The {@link Model} object to be used in {@link Swagger}
     */
    public static Model model(Class<?> clazz) {
        ApiModel annotation = clazz.getAnnotation(ApiModel.class);

        ModelImpl model = new ModelImpl()
                .name(annotation.value())
                .description(annotation.description());

        Arrays.asList(clazz.getFields())
                .stream()
                .filter(field -> field.isAnnotationPresent(ApiModelProperty.class))
                .forEach(field -> {
                    model.property(field.getName(), property(field));
                });

        return model;
    }

    /**
     * Build a {@link Property} object defined by {@link ApiModelProperty} to document
     * the fields of an {@link ApiModel}. The property object returned is going to vary
     * depending on the field type.
     *
     * @param field The field for which to build the property object.
     * @return The {@link Property} object to be used in {@link Model}
     */
    private static Property property(Field field) {
        ApiModelProperty annotation = field.getAnnotation(ApiModelProperty.class);

        Class<?> fieldType = field.getType();

        if (String.class.isAssignableFrom(fieldType)) {
            return new StringProperty()
                    .required(annotation.required())
                    .example(annotation.example().isEmpty() ? null : annotation.example())
                    .description(annotation.value().isEmpty() ? null : annotation.value());
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            MapProperty property = new MapProperty();
            property.setRequired(annotation.required());
            return property
                    .description(annotation.value());
        }
        if (fieldType.isArray()) {
            ArrayProperty property = new ArrayProperty();
            property.setRequired(annotation.required());
            return property
                    .example(annotation.example())
                    .description(annotation.value());
        }
        if (Double.class.isAssignableFrom(fieldType) || "double".equals(fieldType.getName())) {
            DoubleProperty property = new DoubleProperty();
            property.setRequired(annotation.required());
            return property
                    .example(annotation.example())
                    .description(annotation.value());
        }
        if (Float.class.isAssignableFrom(fieldType) || "float".equals(fieldType.getName())) {
            FloatProperty property = new FloatProperty();
            property.setRequired(annotation.required());
            return property
                    .example(annotation.example())
                    .description(annotation.value());
        }
        if (Integer.class.isAssignableFrom(fieldType) || "int".equals(fieldType.getName())) {
            IntegerProperty property = new IntegerProperty();
            property.setRequired(annotation.required());
            property.setExample(annotation.example());
            return property
                    .description(annotation.value());
        }
        if (Long.class.isAssignableFrom(fieldType) || "long".equals(fieldType.getName())) {
            LongProperty property = new LongProperty();
            property.setRequired(annotation.required());
            property.setExample(annotation.example());
            return property
                    .description(annotation.value());
        }
        if (Boolean.class.isAssignableFrom(fieldType) || "boolean".equals(fieldType.getName())) {
            BooleanProperty property = new BooleanProperty();
            property.setRequired(annotation.required());
            property.setExample(annotation.example());
            return property
                    .description(annotation.value());
        }

        ObjectProperty property = new ObjectProperty()
                .required(annotation.required())
                .example(annotation.example())
                .description(annotation.value());
        return property;
    }
}
