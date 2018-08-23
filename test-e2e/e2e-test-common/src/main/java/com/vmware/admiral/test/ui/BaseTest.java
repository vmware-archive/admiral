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

package com.vmware.admiral.test.ui;

import static com.codeborne.selenide.Selenide.close;

import java.util.logging.Logger;

import org.junit.AfterClass;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.test.ui.pages.applications.ApplicationsPageLibrary;
import com.vmware.admiral.test.ui.pages.clusters.ClustersPageLibrary;
import com.vmware.admiral.test.ui.pages.containers.ContainersPageLibrary;
import com.vmware.admiral.test.ui.pages.identity.IdentityManagementPageLibrary;
import com.vmware.admiral.test.ui.pages.logs.LogsPageLibrary;
import com.vmware.admiral.test.ui.pages.networks.NetworksPageLibrary;
import com.vmware.admiral.test.ui.pages.projects.ProjectsPageLibrary;
import com.vmware.admiral.test.ui.pages.registries.GlobalRegistriesPageLibrary;
import com.vmware.admiral.test.ui.pages.repositories.RepositoriesPageLibrary;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPageLibrary;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPageLibrary;

public abstract class BaseTest {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    protected abstract CommonWebClient<?> getClient();

    protected ApplicationsPageLibrary applications() {
        return getClient().applications();
    }

    protected ContainersPageLibrary containers() {
        return getClient().containers();
    }

    protected NetworksPageLibrary networks() {
        return getClient().networks();
    }

    protected VolumesPageLibrary volumes() {
        return getClient().volumes();
    }

    protected TemplatesPageLibrary templates() {
        return getClient().templates();
    }

    protected RepositoriesPageLibrary repositories() {
        return getClient().repositories();
    }

    protected ClustersPageLibrary clusters() {
        return getClient().clusters();
    }

    protected ProjectsPageLibrary projects() {
        return getClient().projects();
    }

    protected GlobalRegistriesPageLibrary registries() {
        return getClient().registries();
    }

    protected IdentityManagementPageLibrary identity() {
        return getClient().identity();
    }

    protected LogsPageLibrary logs() {
        return getClient().logs();
    }

    protected void sleep(int miliseconds) {
        try {
            Thread.sleep(miliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void closeDriver() {
        try {
            close();
        } catch (Throwable e) {

        }
    }

}
