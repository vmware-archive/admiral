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

import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.image.service.FavoriteImagePopulateFlagService.FavoriteImagePopulateFlag;
import com.vmware.xenon.common.LocalizableValidationException;

public class FavoriteImagePopulateFlagServiceTest extends ComputeBaseTest {

    @Before
    public void SetUp() throws Throwable {
        waitForServiceAvailability(FavoriteImagePopulateFlagService.FACTORY_LINK);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostStateWithoutBody() throws Throwable {
        doPost(null, FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testPostSecondState() throws Throwable {
        FavoriteImagePopulateFlag state = new FavoriteImagePopulateFlag();
        state.shouldPopulate = Boolean.TRUE;
        state.shouldPopulateInEmbedded = Boolean.TRUE;
        doPost(state, FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK);
    }

    @Test
    public void testPutState() throws Throwable {
        FavoriteImagePopulateFlag state = new FavoriteImagePopulateFlag();
        state.shouldPopulate = Boolean.FALSE;
        state.shouldPopulateInEmbedded = Boolean.FALSE;
        state.documentSelfLink = FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK;
        FavoriteImagePopulateFlag response = doPut(state);

        assertFalse(response.shouldPopulate);
        assertFalse(response.shouldPopulateInEmbedded);
    }
}