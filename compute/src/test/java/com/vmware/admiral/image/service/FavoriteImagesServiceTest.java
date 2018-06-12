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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.image.service.FavoriteImageFactoryService.RegistryNotValidException;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class FavoriteImagesServiceTest extends ComputeBaseTest {

    public static final String GENERIC_IMAGE_NAME = "library/name";
    public static final String GENERIC_IMAGE_DESCRIPTION = "generic description";

    public static final String DEFAULT_REGISTRY = "https://registry.hub.docker.com";
    public static final String TENANTED_REGISTRY = "https://bellevue-ci.eng.vmware.com:5005";
    public static final String PROJECT_REGISTRY = "https://bellevue-ci.eng.vmware.com:5001";
    public static final String BUSINESS_GROUP_REGISTRY = "https://bellevue-ci.eng.vmware.com:5002";

    public static final List<String> TENANT_LINKS = Collections.singletonList(QueryUtil.TENANT_IDENTIFIER + "ten1");
    public static final List<String> PROJECT_LINKS = Collections.singletonList(QueryUtil.PROJECT_IDENTIFIER + "prj1");
    public static final List<String> BUSINESS_GROUP_LINKS = Collections.singletonList(TENANT_LINKS.get(0) + QueryUtil.GROUP_IDENTIFIER + "grp1");

    public static final FavoriteImage nginxImage = new FavoriteImage();
    public static final FavoriteImage photonImage = new FavoriteImage();
    public static final FavoriteImage alpineImage = new FavoriteImage();
    public static final FavoriteImage nginxWithTenantLink = new FavoriteImage();

    public static final RegistryState disabledRegistry = new RegistryState();
    public static final RegistryState tenantedRegistry = new RegistryState();
    public static final RegistryState projectRegistry = new RegistryState();
    public static final RegistryState businessGroupRegistry = new RegistryState();

    public static final FavoriteImage tenantedRegistryImage = new FavoriteImage();
    public static final FavoriteImage projectRegistryImage = new FavoriteImage();
    public static final FavoriteImage bgRegistryImage = new FavoriteImage();

    @BeforeClass
    public static void initObjects() {
        nginxImage.name = "library/nginx";
        nginxImage.description = "Official build of Nginx.";
        nginxImage.registry = DEFAULT_REGISTRY;

        photonImage.name = "library/photon";
        photonImage.description = "Photon OS is a technology preview of a minimal Linux container host.";
        photonImage.registry = DEFAULT_REGISTRY;

        alpineImage.name = "library/alpine";
        alpineImage.description = "A minimal Docker image based on Alpine Linux with a complete "
                + "package index and only 5 MB in size!";
        alpineImage.registry = DEFAULT_REGISTRY;

        nginxWithTenantLink.name = nginxImage.name;
        nginxWithTenantLink.description = nginxImage.description;
        nginxWithTenantLink.registry = nginxImage.registry;
        nginxWithTenantLink.tenantLinks = TENANT_LINKS;

        disabledRegistry.address = "https://disabled-registry.com";
        disabledRegistry.name = "disabled reg";
        disabledRegistry.disabled = Boolean.TRUE;

        tenantedRegistry.address = TENANTED_REGISTRY;
        tenantedRegistry.name = "tenantedRegistry";
        tenantedRegistry.tenantLinks = TENANT_LINKS;

        projectRegistry.address = PROJECT_REGISTRY;
        projectRegistry.name = "projectRegistry";
        projectRegistry.tenantLinks = PROJECT_LINKS;

        businessGroupRegistry.address = BUSINESS_GROUP_REGISTRY;
        businessGroupRegistry.name = "businessGroupRegistry";
        businessGroupRegistry.tenantLinks = BUSINESS_GROUP_LINKS;

        tenantedRegistryImage.name = GENERIC_IMAGE_NAME;
        tenantedRegistryImage.description = GENERIC_IMAGE_DESCRIPTION;
        tenantedRegistryImage.registry = TENANTED_REGISTRY;

        projectRegistryImage.name = GENERIC_IMAGE_NAME;
        projectRegistryImage.description = GENERIC_IMAGE_DESCRIPTION;
        projectRegistryImage.registry = PROJECT_REGISTRY;

        bgRegistryImage.name = GENERIC_IMAGE_NAME;
        bgRegistryImage.description = GENERIC_IMAGE_DESCRIPTION;
        bgRegistryImage.registry = BUSINESS_GROUP_REGISTRY;
    }

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(FavoriteImageFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryFactoryService.SELF_LINK);

        cleanUpFavoriteImages();
    }

    @Test
    public void testNoAddedFavoriteImages() throws Throwable {
        checkImages();
    }

    @Test
    public void testAddAndRemoveImageFromFavorites() throws Throwable {
        FavoriteImage imageState = addImageToFavorites(nginxImage).getBody(FavoriteImage.class);

        validateImage(nginxImage, imageState);
        checkImages(nginxImage);

        removeImageFromFavorites(imageState);

        checkImages();
    }

    @Test
    public void testAddRemoveImageFromFavoritesWithTenantLinks() throws Throwable {
        FavoriteImage nginxImageState = addImageToFavorites(nginxImage).getBody(FavoriteImage.class);
        FavoriteImage nginxWithTenantLinkState = addImageToFavorites(nginxWithTenantLink).getBody(FavoriteImage.class);

        checkImages(nginxImage, nginxWithTenantLink);

        removeImageFromFavorites(nginxWithTenantLinkState);
        checkImages(nginxImage);

        removeImageFromFavorites(nginxImageState);
        checkImages();
    }

    @Test
    public void testAddRemoveMultipleImagesFromFavorites() throws Throwable {
        FavoriteImage nginxImageState = addImageToFavorites(nginxImage)
                .getBody(FavoriteImage.class);
        FavoriteImage photonImageState = addImageToFavorites(photonImage)
                .getBody(FavoriteImage.class);
        FavoriteImage alpineImageState = addImageToFavorites(alpineImage)
                .getBody(FavoriteImage.class);

        validateImage(nginxImage, nginxImageState);
        validateImage(photonImage, photonImageState);
        validateImage(alpineImage, alpineImageState);
        checkImages(nginxImage, photonImage, alpineImage);

        removeImageFromFavorites(nginxImageState);
        checkImages(photonImage, alpineImage);

        removeImageFromFavorites(photonImageState);
        checkImages(alpineImage);

        removeImageFromFavorites(alpineImageState);
        checkImages();
    }

    @Test
    public void testAddImageToFavoritesNonexistentRegistry() throws Throwable {
        FavoriteImage fictionalRegistryImage = new FavoriteImage();
        fictionalRegistryImage.name = "library/photon";
        fictionalRegistryImage.description = "This is a non-existing image";
        fictionalRegistryImage.registry = "non-existing registry";

        assertAddImageThrowsRegistryNotValidException(fictionalRegistryImage);
        checkImages();
    }

    @Test
    public void testAddImageToFavoritesDisabledRegistry() throws Throwable {
        RegistryState registryState = addRegistry(disabledRegistry).getBody(RegistryState.class);

        assertTrue(registryState.disabled);

        FavoriteImage imageToAdd = new FavoriteImage();
        imageToAdd.name = GENERIC_IMAGE_NAME;
        imageToAdd.description = GENERIC_IMAGE_DESCRIPTION;
        imageToAdd.registry = disabledRegistry.address;

        assertAddImageThrowsRegistryNotValidException(imageToAdd);
        checkImages();
    }

    @Test
    public void testAddImageToFavoritesTenantedRegistry() throws Throwable {
        RegistryState tenantedRegistryState = addRegistry(tenantedRegistry).getBody(RegistryState.class);
        RegistryState projectRegistryState = addRegistry(projectRegistry).getBody(RegistryState.class);
        RegistryState businessGroupRegistryState = addRegistry(businessGroupRegistry).getBody(RegistryState.class);

        assertEquals(TENANT_LINKS, tenantedRegistryState.tenantLinks);
        assertEquals(PROJECT_LINKS, projectRegistryState.tenantLinks);
        assertEquals(BUSINESS_GROUP_LINKS, businessGroupRegistryState.tenantLinks);

        Operation tenantedRegistryImageOp = addImageToFavorites(tenantedRegistryImage);
        FavoriteImage addedImageState = tenantedRegistryImageOp.getBody(FavoriteImage.class);
        assertEquals(Operation.STATUS_CODE_OK, tenantedRegistryImageOp.getStatusCode());
        assertEquals(tenantedRegistryImage, tenantedRegistryImageOp.getBody(FavoriteImage.class));
        checkImages(addedImageState);

        assertAddImageThrowsRegistryNotValidException(projectRegistryImage);
        checkImages(addedImageState);

        assertAddImageThrowsRegistryNotValidException(bgRegistryImage);
        checkImages(addedImageState);
    }

    @Test
    public void testAddExistingImageToFavorites() throws Throwable {
        FavoriteImage nginxImageState = addImageToFavorites(nginxImage)
                .getBody(FavoriteImage.class);

        validateImage(nginxImage, nginxImageState);
        checkImages(nginxImage);

        Operation addOperation = addImageToFavorites(nginxImage);
        FavoriteImage newNginxImageState;
        if (addOperation.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
            newNginxImageState = nginxImageState;
        } else {
            newNginxImageState = addOperation.getBody(FavoriteImage.class);
        }

        assertEquals(nginxImageState.documentSelfLink, newNginxImageState.documentSelfLink);
        checkImages(nginxImage);

        removeImageFromFavorites(nginxImageState);

        checkImages();
    }

    @Test
    public void testFavoriteImageEquals() {
        FavoriteImage nginxImageCopy = new FavoriteImage();
        nginxImageCopy.name = nginxImage.name;
        nginxImageCopy.description = nginxImage.description;
        nginxImageCopy.registry = nginxImage.registry;

        FavoriteImage nullImage = null;

        assertNotSame(nginxImage, nginxImageCopy);
        assertNotSame(nginxImage, photonImage);
        assertNotSame(nginxImage, nullImage);

        assertEquals(nginxImage, nginxImage);
        assertEquals(nginxImage, nginxImageCopy);
        assertEquals(nginxImageCopy, nginxImage);
        assertNotEquals(nginxImage, new Object());
        assertNotEquals(nginxImage, photonImage);
        assertNotEquals(nginxImage, nullImage);

        assertEquals(nginxImageCopy.hashCode(), nginxImage.hashCode());
    }

    private void cleanUpFavoriteImages() throws Throwable {

        List<FavoriteImage> images = getDocumentsOfType(FavoriteImage.class);

        images.forEach(i -> {
            try {
                host.log(Level.INFO, "Removing default image " + i.name);
                removeImageFromFavorites(i);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }

    private void checkImages(FavoriteImage... expectedImages) throws Throwable {
        List<FavoriteImage> favorites = getDocumentsOfType(FavoriteImage.class);

        boolean containsExpectedImages = true;
        for (FavoriteImage i : expectedImages) {
            containsExpectedImages &= favorites.contains(i);
        }

        assertEquals(expectedImages.length, favorites.size());
        assertTrue(containsExpectedImages);
    }

    private Operation addImageToFavorites(FavoriteImage imageToAdd) {
        List<Operation> result = new LinkedList<>();
        Operation addToFavorites = Operation.createPost(
                UriUtils.buildUri(host, ManagementUriParts.FAVORITE_IMAGES))
                .setBody(imageToAdd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Can't favorite image");
                        host.failIteration(e);
                    } else {
                        result.add(o);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(addToFavorites);
        host.testWait();

        return result.get(0);
    }

    private Operation addRegistry(RegistryState registry) {
        List<Operation> result = new LinkedList<>();
        Operation addRegistry = Operation.createPost(
                UriUtils.buildUri(host, RegistryFactoryService.SELF_LINK))
                .setBody(registry)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Can't add registry");
                        host.failIteration(e);
                    } else {
                        result.add(o);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(addRegistry);
        host.testWait();

        return result.get(0);
    }

    private void removeImageFromFavorites(FavoriteImage imageToRemove) {
        Operation removeFromFavorites = Operation.createDelete(
                UriUtils.buildUri(host, imageToRemove.documentSelfLink))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Can't remove image from favorites");
                        host.failIteration(e);
                    } else {
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(removeFromFavorites);
        host.testWait();
    }

    private void assertAddImageThrowsRegistryNotValidException(FavoriteImage imageToAdd) {
        try {
            addImageToFavorites(imageToAdd);
            fail("Expected RegistryNotValidException");
        } catch (Exception e) {
            if (!(e instanceof RegistryNotValidException)) {
                fail(String.format("Expected RegistryNotValidException, but got %s", Utils.toString(e)));
            }
        }
    }

    private void validateImage(FavoriteImage expected, FavoriteImage actual) {
        assertEquals(expected.name, actual.name);
        assertEquals(expected.description, actual.description);
        assertEquals(expected.registry, actual.registry);
    }
}