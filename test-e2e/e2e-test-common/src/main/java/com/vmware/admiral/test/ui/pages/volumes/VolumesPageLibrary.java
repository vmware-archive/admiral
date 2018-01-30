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

package com.vmware.admiral.test.ui.pages.volumes;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;

public class VolumesPageLibrary extends ResourcePageLibrary {

    private final By[] iframeLocators = new By[] { By.cssSelector("#admiral-content-frame") };

    private VolumesPage volumesPage;
    private CreateVolumePage createVolumePage;

    public VolumesPage volumesPage() {
        if (Objects.isNull(volumesPage)) {
            VolumesPageLocators locators = new VolumesPageLocators();
            VolumesPageValidator validator = new VolumesPageValidator(getFrameLocators(),
                    locators);
            volumesPage = new VolumesPage(getFrameLocators(), validator, locators);
        }
        return volumesPage;
    }

    public CreateVolumePage createVolumePage() {
        if (Objects.isNull(createVolumePage)) {
            CreateVolumePageLocators locators = new CreateVolumePageLocators();
            CreateVolumePageValidator validator = new CreateVolumePageValidator(getFrameLocators(),
                    locators);
            createVolumePage = new CreateVolumePage(getFrameLocators(), validator, locators);
        }
        return createVolumePage;
    }

    @Override
    protected By[] getFrameLocators() {
        return iframeLocators;
    }

}
