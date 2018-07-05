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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.image.service.FavoriteImagePopulateFlagService.FavoriteImagePopulateFlag;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class FavoriteImagePopulateInEmbeddedServiceTest extends ComputeBaseTest {

    List<String> tenantLinks;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(FavoriteImageFactoryService.SELF_LINK);
        waitForServiceAvailability(FavoriteImagePopulateFlagService.FACTORY_LINK);
        waitForServiceAvailability(FavoriteImagePopulateInEmbeddedService.SELF_LINK);
        tenantLinks = new ArrayList<>(Arrays.asList(new String[] {"/tenants/qe"}));
    }

    @Test
    public void testPopulateFavoriteImagesInEmbedded() throws Throwable {
        verifyFlag(true);
        verifyGlobalFavoriteImages();

        Operation populateFavoriteImagesForTenants = Operation
                .createPost(UriUtils.buildUri(host, FavoriteImagePopulateInEmbeddedService.SELF_LINK))
                .setBody(tenantLinks)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(populateFavoriteImagesForTenants);
        host.testWait();

        waitForCreationOfTenantedFavoriteImages();

        verifyFlag(false);
        verifyTenantedFavoriteImages();
    }

    private void verifyFlag(boolean state) {
        List<FavoriteImagePopulateFlag> result = new LinkedList<>();
        Operation getFlagOp = Operation
                .createGet(UriUtils.buildUri(host, FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    }
                    FavoriteImagePopulateFlag flag = o.getBody(FavoriteImagePopulateFlag.class);
                    result.add(flag);
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(getFlagOp);
        host.testWait();

        assertFalse(result.get(0).shouldPopulate);
        assertEquals(state, result.get(0).shouldPopulateInEmbedded);
    }

    private void verifyGlobalFavoriteImages() throws Throwable {
        List<FavoriteImage> favoriteImages = getDocumentsOfType(FavoriteImage.class);

        assertFalse(favoriteImages.isEmpty());
        assertEquals(15, favoriteImages.size());
        favoriteImages.forEach(favoriteImage -> assertNull(favoriteImage.tenantLinks));
    }

    private void verifyTenantedFavoriteImages() throws Throwable {
        List<FavoriteImage> favoriteImages = getDocumentsOfType(FavoriteImage.class);

        assertFalse(favoriteImages.isEmpty());
        assertEquals(30 ,favoriteImages.size());
        List<FavoriteImage> tenantedImages = favoriteImages.stream()
                .filter(image -> image.tenantLinks != null)
                .collect(Collectors.toList());
        tenantedImages.forEach(image -> assertEquals(tenantLinks, image.tenantLinks));
    }

    private void waitForCreationOfTenantedFavoriteImages() throws Throwable {
        AtomicBoolean cotinue = new AtomicBoolean();
        waitFor(() -> {
            QueryTask queryTask = QueryUtil.buildQuery(FavoriteImage.class, true,
                    QueryUtil.addTenantClause(tenantLinks));
            ServiceDocumentQuery<FavoriteImage> query = new ServiceDocumentQuery<>(host, FavoriteImage.class);
            query.query(queryTask, (r) -> {
                if (r.hasException()) {
                    host.log("Exception while retrieving FavoriteImage: " +
                            r.getException());
                    cotinue.set(true);
                } else if (r.hasResult()) {
                    if (r.getCount() == 15) {
                        cotinue.set(true);
                    }
                }
            });
            return cotinue.get();
        });
    }
}