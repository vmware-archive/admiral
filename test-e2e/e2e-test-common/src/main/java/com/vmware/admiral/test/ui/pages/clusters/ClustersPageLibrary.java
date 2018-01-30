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

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class ClustersPageLibrary extends PageLibrary {

    private ClustersPage clustersPage;
    private AddClusterModalDialog addHostDialog;
    private DeleteClusterModalDialog deleteHostDialog;
    private HostCertificateModalDialog certificateModalDialog;

    public ClustersPage clustersPage() {
        if (Objects.isNull(clustersPage)) {
            ClustersPageLocators locators = new ClustersPageLocators();
            ClustersPageValidator validator = new ClustersPageValidator(getFrameLocators(),
                    locators);
            clustersPage = new ClustersPage(getFrameLocators(), validator, locators);
        }
        return clustersPage;
    }

    public AddClusterModalDialog addHostDialog() {
        if (Objects.isNull(addHostDialog)) {
            AddClusterModalDialogLocators locators = new AddClusterModalDialogLocators();
            AddClusterModalDialogValidator validator = new AddClusterModalDialogValidator(
                    getFrameLocators(), locators);
            addHostDialog = new AddClusterModalDialog(getFrameLocators(), validator, locators);
        }
        return addHostDialog;
    }

    public DeleteClusterModalDialog deleteHostDialog() {
        if (Objects.isNull(deleteHostDialog)) {
            DeleteClusterModalDialogLocators locators = new DeleteClusterModalDialogLocators();
            DeleteClusterModalDialogValidator validator = new DeleteClusterModalDialogValidator(
                    getFrameLocators(), locators);
            deleteHostDialog = new DeleteClusterModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return deleteHostDialog;
    }

    public HostCertificateModalDialog certificateModalDialog() {
        if (Objects.isNull(certificateModalDialog)) {
            HostCertificateModalDialogLocators locators = new HostCertificateModalDialogLocators();
            HostCertificateModalDialogValidator validator = new HostCertificateModalDialogValidator(
                    getFrameLocators(), locators);
            certificateModalDialog = new HostCertificateModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return certificateModalDialog;
    }

    @Override
    protected By[] getFrameLocators() {
        return null;
    }

}
