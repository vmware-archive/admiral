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

import java.util.*;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.swagger.annotations.*;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;

import org.junit.BeforeClass;

import com.vmware.admiral.common.SwaggerDocumentation;
import com.vmware.admiral.common.test.BaseTestCase;

public class SwaggerDocumentationBaseTestCase extends BaseTestCase {

    protected static final String API_TAG = "apiTag";
    protected static final String PATH = "/service_path";
    protected static final String API_MODEL_DESCRIPTION = "Mock of a service document";
    protected static final String API_MODEL_NAME = "Service Document Mock";

    protected static final String PROPERTY_ONE_NAME = "propOne";
    protected static final String PROPERTY_ONE_DESCRIPTION = "Property one description";
    protected static final String PROPERTY_ONE_EXAMPLE = "Example One.";

    protected static final String PROPERTY_TWO_NAME = "propTwo";
    protected static final String PROPERTY_TWO_DESCRIPTION = "Property two description";
    protected static final String PROPERTY_TWO_EXAMPLE = "Example Two.";

    protected static final String ARRAY_PROP_NAME = "arrayProp";
    protected static final String DOUBLE_PROP_NAME = "doubleProp";
    protected static final String FLOAT_PROP_NAME = "floatProp";
    protected static final String INTEGER_PROP_NAME = "integerProp";
    protected static final String LONG_PROP_NAME = "longProp";
    protected static final String BOOLEAN_PROP_NAME = "booleanProp";
    protected static final String OBJECT_PROP_NAME = "objectProp";

    protected static final String GET_VALUE = "Handle get.";
    protected static final String GET_DESCRIPTION = "Handle get of a single instance.";
    protected static final String GET_NICKNAME = "getInstance";

    protected static final String POST_VALUE = "Handle post.";
    protected static final String POST_DESCRIPTION = "Handle creation of new instance.";
    protected static final String POST_NICKNAME = "createInstance";

    protected static final String BODY_PARAM_NAME = "Body Parameter";
    protected static final String BODY_PARAM_VALUE = "Body Parameter description";
    protected static final String FORM_PARAM_NAME = "Form Parameter";
    protected static final String FORM_PARAM_VALUE = "Form Parameter description";
    protected static final String HEADER_PARAM_NAME = "Header Parameter";
    protected static final String HEADER_PARAM_VALUE = "Header Parameter description";
    protected static final String PATH_PARAM_NAME = "Path Parameter";
    protected static final String PATH_PARAM_VALUE = "Path Parameter description";
    protected static final String QUERY_PARAM_NAME = "Query Parameter";
    protected static final String QUERY_PARAM_VALUE = "Query Parameter description";

    @Api(tags = {API_TAG})
    @Path(PATH)
    protected static class AnnotatedServiceMock {

        @ApiModel(
                description = API_MODEL_DESCRIPTION,
                value = API_MODEL_NAME)
        protected static class AnnotatedServiceDocumentMock {

            @ApiModelProperty(
                    name = PROPERTY_ONE_NAME,
                    value = PROPERTY_ONE_DESCRIPTION,
                    example = PROPERTY_ONE_EXAMPLE,
                    required = true)
            public String propOne;
            @ApiModelProperty(
                    name = PROPERTY_TWO_NAME,
                    value = PROPERTY_TWO_DESCRIPTION,
                    example = PROPERTY_TWO_EXAMPLE,
                    required = true)
            public String propTwo;

            @ApiModelProperty
            public String[] arrayProp;
            @ApiModelProperty
            public Double doubleProp;
            @ApiModelProperty
            public Float floatProp;
            @ApiModelProperty
            public Integer integerProp;
            @ApiModelProperty
            public Long longProp;
            @ApiModelProperty
            public Boolean booleanProp;
            @ApiModelProperty
            public Object objectProp;

        }

        @GET
        @Path(SwaggerDocumentation.INSTANCE_PATH)
        @ApiOperation(
                value = GET_VALUE,
                notes = GET_DESCRIPTION,
                nickname = GET_NICKNAME,
                response = SwaggerDocumentationServiceTest.AnnotatedServiceMock.AnnotatedServiceDocumentMock.class)
        @ApiImplicitParams({
                @ApiImplicitParam(
                        name = FORM_PARAM_NAME,
                        value = FORM_PARAM_VALUE,
                        dataType = SwaggerDocumentation.DataTypes.DATA_TYPE_STRING,
                        paramType = SwaggerDocumentation.ParamTypes.PARAM_TYPE_FORM,
                        required = false),
                @ApiImplicitParam(
                        name = PATH_PARAM_NAME,
                        value = PATH_PARAM_VALUE,
                        dataType = SwaggerDocumentation.DataTypes.DATA_TYPE_STRING,
                        paramType = SwaggerDocumentation.ParamTypes.PARAM_TYPE_PATH,
                        required = false),
                @ApiImplicitParam(
                        name = QUERY_PARAM_NAME,
                        value = QUERY_PARAM_VALUE,
                        dataType = SwaggerDocumentation.DataTypes.DATA_TYPE_STRING,
                        paramType = SwaggerDocumentation.ParamTypes.PARAM_TYPE_QUERY,
                        required = true)})
        public void handleGet() {}

        @POST
        @Path(SwaggerDocumentation.BASE_PATH)
        @ApiOperation(
                value = POST_VALUE,
                notes = POST_DESCRIPTION,
                nickname = POST_NICKNAME)
        @ApiImplicitParams({
                @ApiImplicitParam(
                        name = BODY_PARAM_NAME,
                        value = BODY_PARAM_VALUE,
                        dataType = SwaggerDocumentation.DataTypes.DATA_TYPE_OBJECT,
                        dataTypeClass = SwaggerDocumentationServiceTest.AnnotatedServiceMock.AnnotatedServiceDocumentMock.class,
                        paramType = SwaggerDocumentation.ParamTypes.PARAM_TYPE_BODY,
                        required = true),
                @ApiImplicitParam(
                        name = HEADER_PARAM_NAME,
                        value = HEADER_PARAM_VALUE,
                        dataType = SwaggerDocumentation.DataTypes.DATA_TYPE_STRING,
                        paramType = SwaggerDocumentation.ParamTypes.PARAM_TYPE_HEADER,
                        required = true)})
        public void handlePost() {}

    }

    protected static List<String> expectedTags;
    protected static BodyParameter bodyParameter;
    protected static FormParameter formParameter;
    protected static HeaderParameter headerParameter;
    protected static PathParameter pathParameter;
    protected static QueryParameter queryParameter;
    protected static Model model;
    protected static io.swagger.models.Path getPath;
    protected static io.swagger.models.Path postPath;
    protected static Swagger expectedSwagger;

    @BeforeClass
    public static void setUpObjects() {
        expectedTags = new ArrayList<>();
        expectedTags.add(API_TAG);

        bodyParameter = new BodyParameter()
                .name(BODY_PARAM_NAME)
                .description(BODY_PARAM_VALUE)
                .schema(new RefModel().asDefault("AnnotatedServiceDocumentMock"));
        bodyParameter.setRequired(true);

        formParameter = new FormParameter()
                .name(FORM_PARAM_NAME)
                .description(FORM_PARAM_VALUE)
                .required(false)
                .type(SwaggerDocumentation.DataTypes.DATA_TYPE_STRING);

        headerParameter = new HeaderParameter()
                .name(HEADER_PARAM_NAME)
                .description(HEADER_PARAM_VALUE)
                .required(true)
                .type(SwaggerDocumentation.DataTypes.DATA_TYPE_STRING);

        pathParameter = new PathParameter()
                .name(PATH_PARAM_NAME)
                .description(PATH_PARAM_VALUE)
                .required(false)
                .type(SwaggerDocumentation.DataTypes.DATA_TYPE_STRING);

        queryParameter = new QueryParameter()
                .name(QUERY_PARAM_NAME)
                .description(QUERY_PARAM_VALUE)
                .required(true)
                .type(SwaggerDocumentation.DataTypes.DATA_TYPE_STRING);

        model = new ModelImpl().name(API_MODEL_NAME).description(API_MODEL_DESCRIPTION);
        model.setProperties(initProperties());

        getPath = new io.swagger.models.Path()
                .get(new Operation()
                        .operationId(GET_NICKNAME)
                        .summary(GET_VALUE)
                        .description(GET_DESCRIPTION)
                        .tags(expectedTags)
                        .parameter(formParameter)
                        .parameter(pathParameter)
                        .parameter(queryParameter));

        postPath = new io.swagger.models.Path()
                .post(new Operation()
                        .operationId(POST_NICKNAME)
                        .summary(POST_VALUE)
                        .description(POST_DESCRIPTION)
                        .tags(expectedTags)
                        .parameter(bodyParameter)
                        .parameter(headerParameter));

        expectedSwagger = new Swagger()
                .basePath("/")
                .host("host")
                .schemes(Arrays.asList(new Scheme[] {Scheme.HTTP}));

        expectedSwagger.addDefinition("AnnotatedServiceDocumentMock", model);
        expectedSwagger.tags(expectedTags.stream().map(tag -> new Tag().name(tag)).collect(Collectors.toList()));
        expectedSwagger.path(PATH + "/{id}", getPath);
        expectedSwagger.path(PATH + "/", postPath);

    }

    private static Map<String, Property> initProperties() {
        Map<String, Property> properties = new LinkedHashMap<>();
        properties.put(PROPERTY_ONE_NAME, new StringProperty()
                .example(PROPERTY_ONE_EXAMPLE)
                .required(true)
                .description(PROPERTY_ONE_DESCRIPTION));
        properties.put(PROPERTY_TWO_NAME, new StringProperty()
                .example(PROPERTY_TWO_EXAMPLE)
                .required(true)
                .description(PROPERTY_TWO_DESCRIPTION));

        IntegerProperty integerProp = new IntegerProperty();
        LongProperty longProp = new LongProperty();
        BooleanProperty booleanProp = new BooleanProperty();
        integerProp.setExample("");
        longProp.setExample("");
        booleanProp.setExample("");

        properties.put(ARRAY_PROP_NAME, new ArrayProperty().example("").description(""));
        properties.put(DOUBLE_PROP_NAME, new DoubleProperty().example("").description(""));
        properties.put(FLOAT_PROP_NAME, new FloatProperty().example("").description(""));
        properties.put(INTEGER_PROP_NAME, integerProp.description(""));
        properties.put(LONG_PROP_NAME, longProp.description(""));
        properties.put(BOOLEAN_PROP_NAME, booleanProp.description(""));
        properties.put(OBJECT_PROP_NAME, new ObjectProperty().example("").description(""));

        return properties;
    }
}
