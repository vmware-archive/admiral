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

import java.util.LinkedList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class FavoriteImageFactoryService extends AbstractSecuredFactoryService {
    public static final String SELF_LINK = ManagementUriParts.FAVORITE_IMAGES;

    public FavoriteImageFactoryService() {
        super(FavoriteImage.class);
    }

    /**
     * Thrown when the image which is to be added to favorites is already added.
     */
    public class FavoriteImageAlreadyExistsException extends Exception {
        public FavoriteImageAlreadyExistsException(String message) {
            super(message);
        }
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
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }

    @Override
    public void handlePost(Operation op) {
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
                            post.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                            post.fail(new FavoriteImageAlreadyExistsException("Image " +
                                    "already exists as favorite"));
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
                        if (registry.tenantLinks != null || (registry.disabled != null &&
                                registry.disabled.equals(Boolean.TRUE))) {
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
