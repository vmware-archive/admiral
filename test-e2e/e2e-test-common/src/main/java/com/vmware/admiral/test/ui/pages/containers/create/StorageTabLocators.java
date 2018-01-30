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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

public class StorageTabLocators extends CreateContainerPageLocators {

    private final By LAST_VOLUME_HOST_PATH_INPUT = By.cssSelector(
            ".container-volumes-input .multicolumn-input:last-child input[name='host']");
    private final By LAST_VOLUME_CONTAINER_PATH_INPUT = By.cssSelector(
            ".container-volumes-input .multicolumn-input:last-child input[name='container']");
    private final By ADD_VOLUME_INPUT_BUTTON = By
            .cssSelector(".container-volumes-input .multicolumn-input:last-child .fa.fa-plus");
    private final By LAST_VOLUME_READ_ONLY_CHECKBOX = By.cssSelector(
            ".container-volumes-input .multicolumn-input:last-child .inline-input[name='readOnly']");

    public By addVolumeInputButton() {
        return ADD_VOLUME_INPUT_BUTTON;
    }

    public By lastVolumeHostPathInput() {
        return LAST_VOLUME_HOST_PATH_INPUT;
    }

    public By lastVolumeContainerPathInput() {
        return LAST_VOLUME_CONTAINER_PATH_INPUT;
    }

    public By lastVolumeReadOnlyCheckbox() {
        return LAST_VOLUME_READ_ONLY_CHECKBOX;
    }

}
