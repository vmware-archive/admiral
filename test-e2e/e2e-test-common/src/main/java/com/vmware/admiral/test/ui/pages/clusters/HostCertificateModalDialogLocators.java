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

package com.vmware.admiral.test.ui.pages.clusters;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogLocators;

public class HostCertificateModalDialogLocators extends ModalDialogLocators {

    private By ACCEPTC_CERTIFICATE_BITTON = By.cssSelector("verify-certificate .btn.btn-primary");
    private By CANCEL_CERTIFICATE_BITTON = By
            .cssSelector("verify-certificate .btn.btn-outline:not(.show-certificate-btn)");
    private By MODAL_TITLE = By.cssSelector(".modal-content .modal-content .modal-title");
    private By MODAL_CONTENT = By.cssSelector(".modal-content .modal-content");

    @Override
    public By submitButton() {
        return ACCEPTC_CERTIFICATE_BITTON;
    }

    @Override
    public By cancelButton() {
        return CANCEL_CERTIFICATE_BITTON;
    }

    @Override
    public By modalTitle() {
        return MODAL_TITLE;
    }

    @Override
    public By modalContent() {
        return MODAL_CONTENT;
    }

}
