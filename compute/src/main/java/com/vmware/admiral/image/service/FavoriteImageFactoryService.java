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

import static com.vmware.admiral.common.SwaggerDocumentation.BASE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_BODY;
import static com.vmware.admiral.common.SwaggerDocumentation.ParamTypes.PARAM_TYPE_QUERY;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.FAVORITE_IMAGES_TAG;

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.TenantLinksUtil;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

@Api(tags = {FAVORITE_IMAGES_TAG})
@Path(FavoriteImageFactoryService.SELF_LINK)
public class FavoriteImageFactoryService extends AbstractSecuredFactoryService {
    public static final String SELF_LINK = ManagementUriParts.FAVORITE_IMAGES;

    public FavoriteImageFactoryService() {
        super(FavoriteImage.class);
    }

    /**
     * Thrown when the registry an image belongs to is either not global or disabled,
     * or both.
     */
    public class RegistryNotValidException extends Exception {
        public RegistryNotValidException(String message) {
            super(message);
        }
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new FavoriteImagesService();
    }

    @Override
    @GET
    @Path(BASE_PATH)
    @ApiOperation(
            value = "Get all favorite images.",
            notes = "Retrieves all favorite images from the database. Images are project global, " +
                    "which means that all projects have the same favorites")
    @ApiResponses({@ApiResponse(code = Operation.STATUS_CODE_OK,
            message = "Successfully retrieved all favorite images.")})
    @ApiImplicitParams({@ApiImplicitParam(name = "expand", value = "Expand option to view details of the instances",
                    required = false, dataType = "boolean", paramType = PARAM_TYPE_QUERY)})
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }

    @Override
    @POST
    @Path(BASE_PATH)
    @ApiOperation(
            value = "Create a new favorite image.",
            notes = "Adds the specified image to favorites. " +
                    "An image which already exists as favorite will not be added. " +
                    "An image whose registry is either disabled or not present will not be added.")
    @ApiResponses(value = {
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "Image successfully added to favorites."),
            @ApiResponse(code = Operation.STATUS_CODE_NOT_MODIFIED, message = "Image already exists as favorite."),
            @ApiResponse(code = Operation.STATUS_CODE_BAD_REQUEST, message = "Image registry non existent or disabled.")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Container Image", value = "The container image to add to favorites.",
                    paramType = PARAM_TYPE_BODY, dataType = "FavoriteImage", dataTypeClass = FavoriteImage.class)})
    public void handlePost(Operation op) {
        /**
         * If it is an internal xenon request, proceed with the operation.
         */
        if (op.isSynchronize()) {
            super.handlePost(op);
            return;
        }
        completeOrFailOperationForImage(op);
    }

    /**
     * Completes the favorite image operation if the image trying to be added
     * is not already present as favorite, fails otherwise.
     *
     * @param post Operation to add image to favorites.
     */
    private void completeOrFailOperationForImage(Operation post) {
        FavoriteImage imageToFavorite = post.getBody(FavoriteImage.class);
        AssertUtil.assertNotNullOrEmpty(imageToFavorite.name, "imageToFavorite.name");
        AssertUtil.assertNotNullOrEmpty(imageToFavorite.registry, "imageToFavorite.registry");

        QueryTask queryTask = QueryUtil.buildQuery(FavoriteImage.class, true);
        Query nameClause = new Query()
                .setTermPropertyName(FavoriteImage.FIELD_NAME_NAME)
                .setTermMatchValue(imageToFavorite.name)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);
        Query registryClause = new Query()
                .setTermPropertyName(FavoriteImage.FIELD_NAME_REGISTRY)
                .setTermMatchValue(imageToFavorite.registry)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        queryTask.querySpec.query.addBooleanClause(nameClause);
        queryTask.querySpec.query.addBooleanClause(registryClause);

        if (imageToFavorite.tenantLinks != null && !imageToFavorite.tenantLinks.isEmpty()) {
            Query tenantLinkClause = QueryUtil.addTenantClause(imageToFavorite.tenantLinks);
            queryTask.querySpec.query.addBooleanClause(tenantLinkClause);
        }

        QueryUtil.addExpandOption(queryTask);

        List<FavoriteImage> existingFavorites = new LinkedList<>();
        new ServiceDocumentQuery<>(getHost(), FavoriteImage.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        existingFavorites.add(r.getResult());
                    } else {
                        if (existingFavorites.isEmpty()) {
                            completeOrFailOperationForRegistry(post, imageToFavorite.registry);
                        } else {
                            //If the image is already added, add the existing image state to the POST body
                            post.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
                            post.setBody(existingFavorites.get(0));
                            post.complete();
                        }
                    }
                });
    }

    /**
     * Checks whether the registry of the image trying to be added to favorites
     * exists as active and global. If it does not, the operation should fail.
     *
     * @param post            Operation to add image to favorites.
     * @param registryAddress Registry address of the image.
     */
    private void completeOrFailOperationForRegistry(Operation post, String registryAddress) {
        QueryTask queryTask = QueryUtil.buildQuery(RegistryState.class, true);
        Query searchClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_ADDRESS)
                .setTermMatchValue(registryAddress)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        queryTask.querySpec.query.addBooleanClause(searchClause);
        QueryUtil.addExpandOption(queryTask);

        List<RegistryState> registries = new LinkedList<>();
        new ServiceDocumentQuery<>(getHost(), RegistryState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        RegistryState registry = r.getResult();

                        boolean registryInvalid = (Boolean.TRUE.equals(registry.disabled)) ||
                                (registry.tenantLinks != null &&
                                registry.tenantLinks
                                        .stream()
                                        .anyMatch(tenantLink -> TenantLinksUtil.isProjectLink(tenantLink) ||
                                                TenantLinksUtil.isGroupLink(tenantLink)));
                        if (registryInvalid) {
                            post.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                            post.fail(new RegistryNotValidException("The registry of the image "
                                    + "is either project-specific or disabled."));
                        } else {
                            registries.add(registry);
                        }
                    } else {
                        if (registries.isEmpty()) {
                            post.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                            post.fail(new RegistryNotValidException("The registry of the image "
                                    + "does not exist"));
                        } else {
                            super.handlePost(post);
                        }
                    }
                });
    }
}
