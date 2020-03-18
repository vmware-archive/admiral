/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host.swagger;

import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.bodyParameter;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.formParameter;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.getApiMethods;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.getApiTagsAsList;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.headerParameter;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.methodPath;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.model;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.pathParameter;
import static com.vmware.admiral.host.swagger.SwaggerDocumentationUtils.queryParameter;
import static com.vmware.xenon.common.Operation.STATUS_CODE_OK;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.jaxrs.PATCH;
import io.swagger.models.Info;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.properties.RefProperty;
import org.reflections.Reflections;

import com.vmware.admiral.common.SwaggerDocumentation;

/**
 * Used to set up a {@link Swagger} object to be used for API documentation.
 */
public class SwaggerDocumentationAssembler {

    private Swagger swagger;
    private List<String> includePackages;

    private SwaggerDocumentationAssembler() {
        this.swagger = new Swagger();
    }

    /**
     * @return A new instance of {@link SwaggerDocumentationAssembler}
     */
    public static SwaggerDocumentationAssembler create() {
        return new SwaggerDocumentationAssembler();
    }

    /**
     * Specify the {@link Info} object to be used by Swagger. This object
     * contains general information about the API such as the title, license, etc.
     *
     * @param info The {@link Info} object used by Swagger
     * @return This instance of {@link SwaggerDocumentationAssembler}
     */
    public SwaggerDocumentationAssembler setInfo(Info info) {
        this.swagger.setInfo(info);
        return this;
    }

    /**
     * Specify the base address on which all documented services are available.
     *
     * @param host The address as string
     * @return This instance of {@link SwaggerDocumentationAssembler}
     */
    public SwaggerDocumentationAssembler setHost(String host) {
        this.swagger.setHost(host);
        return this;
    }

    /**
     * Specify the base path under which all documented services are available.
     *
     * @param basePath The base path
     * @return This instance of {@link SwaggerDocumentationAssembler}
     */
    public SwaggerDocumentationAssembler setBasePath(String basePath) {
        this.swagger.setBasePath(basePath);
        return this;
    }

    /**
     * Specify which packages to search for documented classes. The packages are
     * passed as an array of Strings with the format "com.vmware.admiral.etc".
     *
     * @param includePackages The array of packages to be searched for documentation
     * @return This instance of {@link SwaggerDocumentationAssembler}
     */
    public SwaggerDocumentationAssembler setIncludePackages(String[] includePackages) {
        this.includePackages = Arrays.asList(includePackages);
        return this;
    }

    /**
     * Specify the HTTP schemes to be used in making requests.
     *
     * @param schemes The specified schemes
     * @return This instance of {@link SwaggerDocumentationAssembler}
     */
    public SwaggerDocumentationAssembler setSchemes(Scheme[] schemes) {
        this.swagger.setSchemes(Arrays.asList(schemes));
        return this;
    }

    /**
     * Sets up the {@link Swagger} object with all the gathered necessary information
     * and returns its instance.
     *
     * @return The assembled {@link Swagger}
     */
    public Swagger build() {
        prepareSwagger();
        return this.swagger;
    }

    /**
     * Retrieve all documented API classes and all documented API Models and populate
     * them in {@link Swagger} accordingly. The API classes are all classes annotated with
     * {@link Api} which are used to handle REST calls. The API Models are all classes
     * annotated with {@link ApiModel} and are representations of objects which are
     * persisted in the database. These models can be used as request bodies or as request
     * responses.
     */
    private void prepareSwagger() {

        includePackages.forEach(includePackage -> {
            Reflections reflections = new Reflections(includePackage);

            reflections.getTypesAnnotatedWith(ApiModel.class)
                    .forEach(clazz -> createApiModel(clazz));

            reflections.getTypesAnnotatedWith(Api.class)
                    .forEach(clazz -> createApi(clazz));
        });

    }

    /**
     * Add the {@code clazz} model to Swagger. The model is a class annotated with
     * {@link ApiModel}. The definition in {@link Swagger} is in the form of a map
     * with the keys being the class names and the values - a {@link Model} object
     * defined by Swagger. The models contain different {@link io.swagger.models.properties.Property}
     * objects which vary depending on the class fields.
     *
     * @param clazz The class which specifies the model.
     */
    private void createApiModel(Class<?> clazz) {
        swagger.addDefinition(clazz.getSimpleName(), model(clazz));
    }

    /**
     * Add the {@code clazz} tags and operations to Swagger. The tags are documented
     * in the {@link Api} annotation and are used to logically group REST operations
     * under concrete lists. The operations are methods annotated by {@link ApiOperation}
     * and are used to handle REST calls to the service.
     *
     * @param clazz The service class
     */
    private void createApi(Class<?> clazz) {
        setTags(clazz);
        setPaths(clazz);
    }

    /**
     * Add the service's tags to Swagger.
     *
     * @param clazz The service class
     */
    private void setTags(Class<?> clazz) {
        if (this.swagger.getTags() == null) {
            this.swagger.setTags(new LinkedList<>());
        }

        this.swagger.getTags().addAll(getApiTagsAsList(clazz));
    }

    /**
     * Add the REST operation descriptions to Swagger. The descriptions are stored
     * in {@link Swagger#paths} which is a map, containing the path of the operation
     * as key and the descriptions of the different HTTP methods as value. The descriptions
     * are stored in a separate {@link io.swagger.models.Path} object which has an
     * {@link Operation} definition for each method.
     *
     * @param clazz The service class
     */
    private void setPaths(Class<?> clazz) {
        String rootPath = clazz.getAnnotation(Path.class).value();
        String[] operationTags = clazz.getAnnotation(Api.class).tags();

        getApiMethods(clazz).forEach(method -> {
            String path = rootPath + methodPath(method);

            if (this.swagger.getPath(path) == null) {
                this.swagger.path(path, new io.swagger.models.Path());
            }

            if (method.isAnnotationPresent(GET.class)) {
                this.swagger.getPath(path).get(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(PUT.class)) {
                this.swagger.getPath(path).put(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(POST.class)) {
                this.swagger.getPath(path).post(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(PATCH.class)) {
                this.swagger.getPath(path).patch(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(OPTIONS.class)) {
                this.swagger.getPath(path).options(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(HEAD.class)) {
                this.swagger.getPath(path).head(createOperation(method, operationTags));
            }
            if (method.isAnnotationPresent(DELETE.class)) {
                this.swagger.getPath(path).delete(createOperation(method, operationTags));
            }
        });
    }

    /**
     * Add the description of a specific HTTP method to Swagger. The method is
     * documented in an {@link Operation} object, which contains the method's description.
     *
     * @param method
     * @param operationTags
     * @return
     */
    private Operation createOperation(Method method, String[] operationTags) {
        ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        ApiImplicitParams apiParams = method.getAnnotation(ApiImplicitParams.class);

        Operation operation = new Operation()
                .deprecated(method.isAnnotationPresent(Deprecated.class))
                .operationId(apiOperation.nickname())
                .summary(apiOperation.value())
                .description(apiOperation.notes())
                .tags(Arrays.asList(operationTags));

        if (apiResponses != null) {
            Arrays.asList(apiResponses.value())
                    .forEach(apiResponse -> {
                        Response responseMessage = new Response().description(apiResponse.message());
                        RefProperty responseObject = new RefProperty();

                        /**
                         * Use the {@link ApiOperation#response()} for status code 200 if defined.
                         * Otherwise use the {@link ApiResponse#response()} if defined.
                         */
                        if (apiResponse.code() == STATUS_CODE_OK && !apiOperation.response().equals(Void.class)) {
                            responseObject.asDefault(apiOperation.response().getSimpleName());
                            responseMessage.schema(responseObject);
                        } else if (!apiResponse.response().equals(Void.class)) {
                            responseObject.asDefault(apiResponse.response().getSimpleName());
                            responseMessage.schema(responseObject);
                        }

                        operation.response(apiResponse.code(), responseMessage);
                    });
        }

        if (apiParams != null) {
            Arrays.asList(apiParams.value())
                    .forEach(apiParam -> {
                        setOperationParam(operation, apiParam);
                    });
        }

        return operation;
    }

    /**
     * Add the {@code operation} parameter depending on its type.
     *
     * @param operation The operation for which to add a parameter description
     * @param apiParam  The parameter description
     */
    private void setOperationParam(Operation operation, ApiImplicitParam apiParam) {
        switch (apiParam.paramType()) {
        case SwaggerDocumentation.ParamTypes.PARAM_TYPE_BODY:
            operation.parameter(bodyParameter(apiParam));
            break;
        case SwaggerDocumentation.ParamTypes.PARAM_TYPE_FORM:
            operation.parameter(formParameter(apiParam));
            break;
        case SwaggerDocumentation.ParamTypes.PARAM_TYPE_HEADER:
            operation.parameter(headerParameter(apiParam));
            break;
        case SwaggerDocumentation.ParamTypes.PARAM_TYPE_PATH:
            operation.parameter(pathParameter(apiParam));
            break;
        case SwaggerDocumentation.ParamTypes.PARAM_TYPE_QUERY:
            operation.parameter(queryParameter(apiParam));
            break;
        default:
            break;
        }
    }

}
