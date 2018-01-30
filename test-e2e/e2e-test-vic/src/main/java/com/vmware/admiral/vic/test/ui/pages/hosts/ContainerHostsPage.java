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

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.clusters.ClustersPage;
import com.vmware.admiral.test.ui.pages.clusters.ClustersPageLocators;

public class ContainerHostsPage extends ClustersPage {

    public ContainerHostsPage(By[] iFrameLocators, ContainerHostsPageValidator validator,
            ClustersPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

}
