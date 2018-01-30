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

package com.vmware.admiral.vic.test.ui.pages.hosts;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialogLocators;
import com.vmware.admiral.test.ui.pages.clusters.ClustersPageLibrary;
import com.vmware.admiral.test.ui.pages.clusters.ClustersPageLocators;
import com.vmware.admiral.test.ui.pages.clusters.DeleteClusterModalDialogLocators;

public class ContainerHostsPageLibrary extends ClustersPageLibrary {

    private ContainerHostsPage containerHosts;
    private AddContainerHostModalDialog addHostModalDialog;
    private DeleteContainerHostModalDialog deleteHostModalDialog;

    @Override
    public ContainerHostsPage clustersPage() {
        if (Objects.isNull(containerHosts)) {
            ClustersPageLocators locators = new ClustersPageLocators();
            ContainerHostsPageValidator validator = new ContainerHostsPageValidator(
                    getFrameLocators(), locators);
            containerHosts = new ContainerHostsPage(getFrameLocators(), validator, locators);
        }
        return containerHosts;
    }

    @Override
    public AddContainerHostModalDialog addHostDialog() {
        if (Objects.isNull(addHostModalDialog)) {
            AddClusterModalDialogLocators locators = new AddClusterModalDialogLocators();
            AddContainerHostModalDialogValidator validator = new AddContainerHostModalDialogValidator(
                    getFrameLocators(), locators);
            addHostModalDialog = new AddContainerHostModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return addHostModalDialog;
    }

    @Override
    public DeleteContainerHostModalDialog deleteHostDialog() {
        if (Objects.isNull(deleteHostModalDialog)) {
            DeleteClusterModalDialogLocators locators = new DeleteClusterModalDialogLocators();
            DeleteContainerHostModalDialogValidator validator = new DeleteContainerHostModalDialogValidator(
                    getFrameLocators(), locators);
            deleteHostModalDialog = new DeleteContainerHostModalDialog(getFrameLocators(),
                    validator, locators);
        }
        return deleteHostModalDialog;
    }

}
