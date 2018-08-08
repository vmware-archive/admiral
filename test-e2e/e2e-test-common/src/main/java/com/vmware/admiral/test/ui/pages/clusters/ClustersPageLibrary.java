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

import com.vmware.admiral.test.ui.pages.common.CertificateModalDialog;
import com.vmware.admiral.test.ui.pages.common.CertificateModalDialogLocators;
import com.vmware.admiral.test.ui.pages.common.CertificateModalDialogValidator;
import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class ClustersPageLibrary extends PageLibrary {

    public ClustersPageLibrary(By[] iframeLocators) {
        super(iframeLocators);
    }

    private ClustersPage clustersPage;
    private AddClusterPage addClusterPage;
    private DeleteClusterModalDialog deleteHostDialog;
    private CertificateModalDialog certificateModalDialog;
    private ClusterDetailsPage clusterDetailsPage;
    private ResourcesTab resourcesTab;

    public ClustersPage clustersPage() {
        if (Objects.isNull(clustersPage)) {
            ClustersPageLocators locators = new ClustersPageLocators();
            ClustersPageValidator validator = new ClustersPageValidator(getFrameLocators(),
                    locators);
            clustersPage = new ClustersPage(getFrameLocators(), validator, locators);
        }
        return clustersPage;
    }

    public AddClusterPage addClusterPage() {
        if (Objects.isNull(addClusterPage)) {
            AddClusterPageLocators locators = new AddClusterPageLocators();
            AddClusterPageValidator validator = new AddClusterPageValidator(
                    getFrameLocators(), locators);
            addClusterPage = new AddClusterPage(getFrameLocators(), validator, locators);
        }
        return addClusterPage;
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

    public CertificateModalDialog certificateModalDialog() {
        if (Objects.isNull(certificateModalDialog)) {
            CertificateModalDialogLocators locators = new CertificateModalDialogLocators();
            CertificateModalDialogValidator validator = new CertificateModalDialogValidator(
                    getFrameLocators(), locators);
            certificateModalDialog = new CertificateModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return certificateModalDialog;
    }

    public ClusterDetailsPage clusterDetailsPage() {
        if (Objects.isNull(clusterDetailsPage)) {
            ClusterDetailsPageLocators locators = new ClusterDetailsPageLocators();
            ClusterDetailsPageValidator validator = new ClusterDetailsPageValidator(
                    getFrameLocators(), locators);
            clusterDetailsPage = new ClusterDetailsPage(getFrameLocators(), validator, locators);
        }
        return clusterDetailsPage;
    }

    public ResourcesTab resourcesTab() {
        if (Objects.isNull(resourcesTab)) {
            ResourcesTabLocators locators = new ResourcesTabLocators();
            ResourcesTabValidator validator = new ResourcesTabValidator(getFrameLocators(),
                    locators);
            resourcesTab = new ResourcesTab(getFrameLocators(), validator, locators);
        }
        return resourcesTab;
    }

}
