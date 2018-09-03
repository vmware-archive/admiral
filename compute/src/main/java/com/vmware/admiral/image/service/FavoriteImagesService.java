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

package com.vmware.admiral.image.service;

import static com.vmware.admiral.common.SwaggerDocumentation.INSTANCE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.FAVORITE_IMAGES_TAG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.google.gson.JsonSyntaxException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

@Api(tags = {FAVORITE_IMAGES_TAG})
@Path(FavoriteImagesService.FACTORY_LINK)
public class FavoriteImagesService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.FAVORITE_IMAGES;
    private static final String POPULAR_IMAGES_FILE = "/popular-images.json";

    public FavoriteImagesService() {
        super(FavoriteImage.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    public static List<FavoriteImage> buildDefaultFavoriteImages(ServiceHost host) {
        List<FavoriteImage> images = new ArrayList<>();
        try {
            List jsonImages = Utils.fromJson(FileUtil.getClasspathResourceAsString(POPULAR_IMAGES_FILE), List.class);

            host.log(Level.INFO, "Default favorite images loaded.");

            Function<FavoriteImage, String> createSelfLink = state -> {
                return UriUtils.buildUriPath(FavoriteImagesService.FACTORY_LINK,
                        new StringBuilder().append(state.registry.replaceFirst("https?://", "")
                        .replaceAll("\\.", "-"))
                                .append('-')
                                .append(state.name.replaceAll("/", "-")
                                .replaceAll("\\.", "-"))
                                .toString());
            };

            jsonImages.forEach(i -> {
                Map<String, String> imgObj = (Map<String, String>) i;
                FavoriteImage state = new FavoriteImage();
                state.name = imgObj.get(FavoriteImage.FIELD_NAME_NAME);
                state.description = imgObj.get(FavoriteImage.FIELD_NAME_DESCRIPTION);
                state.registry = imgObj.get(FavoriteImage.FIELD_NAME_REGISTRY);
                state.documentSelfLink = createSelfLink.apply(state);
                images.add(state);
            });
        } catch (NullPointerException | JsonSyntaxException e) {
            host.log(Level.WARNING, "Unable to load default favorite images. " +
                    "Either the file is missing or it is malformed");
        }
        return images;
    }

//    @ApiModel
    public static class FavoriteImage extends MultiTenantDocument {

        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION = "description";
        public static final String FIELD_NAME_REGISTRY = "registry";

        @ApiModelProperty(
                value = "The name of the favorite image.",
                example = "libarary/nginx",
                required = true)
        public String name;
        @ApiModelProperty(
                value = "A description of the favorite image.",
                example = "Official build of Nginx.")
        public String description;
        @ApiModelProperty(
                value = "The registry to which the favorite image belongs.",
                example = "https://registry.hub.docker.com",
                required = true)
        public String registry;

        @Override
        public int hashCode() {
            return Objects.hash(name, description, registry);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FavoriteImage)) {
                return false;
            }
            FavoriteImage i = (FavoriteImage) obj;
            boolean tenantLinkClause = (i.tenantLinks != null ? i.tenantLinks : Collections.emptyList()).equals(
                    (this.tenantLinks != null ? this.tenantLinks : Collections.emptyList()));

            return i.name.equals(this.name) &&
                    i.registry.equals(this.registry) &&
                    tenantLinkClause;
        }
    }

    @Override
    @GET
    @Path(INSTANCE_PATH)
    @ApiOperation(
            value = "Get specific favorite image.",
            notes = "Retrieve a specific favorite image instance based on the provided id.",
            response = FavoriteImage.class)
    @ApiResponses(value = {
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Successfully retrieved image."),
            @ApiResponse(code = Operation.STATUS_CODE_NOT_FOUND, message = "Image with specified id not in favorites.")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "The id of the image", required = true, dataType = "string",
                    paramType = PARAM_TYPE_PATH)})
    public void handleGet(Operation get) {
        super.handleGet(get);
    }

    @Override
    @DELETE
    @Path(INSTANCE_PATH)
    @ApiOperation(
            value = "Remove specific image from favorites.",
            notes = "Removes a specific image from favorites based on the provided id.")
    @ApiResponses(value = {
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Successfully removed image from favorites.")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "The id of the image", required = true, dataType = "string",
                    paramType = PARAM_TYPE_PATH)})
    public void handleDelete(Operation delete) {
        super.handleDelete(delete);
    }
}
