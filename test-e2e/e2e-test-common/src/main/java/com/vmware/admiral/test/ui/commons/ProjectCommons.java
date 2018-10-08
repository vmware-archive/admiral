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

package com.vmware.admiral.test.ui.commons;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.test.ui.pages.projects.AddProjectPage;

public class ProjectCommons {

    public static void addProject(CommonWebClient<?> client, String name, String description,
            boolean isPublic) {
        client.projects().projectsPage().clickAddProjectButton();
        AddProjectPage addProjectPage = client.projects().addProjectPage();
        addProjectPage.waitToLoad();
        addProjectPage.setName(name);
        if (Objects.nonNull(description)) {
            addProjectPage.setDescription(description);
        }
        if (isPublic) {
            addProjectPage.setIsPublic(true);
        }
        addProjectPage.clickCreateButton();
        client.projects().projectsPage().validate().validateProjectIsVisible(name);
    }

    public static void deleteProject(CommonWebClient<?> client, String projectName) {
        client.projects().projectsPage().clickProjectDeleteButton(projectName);
        client.projects().deleteProjectDialog().waitToLoad();
        client.projects().deleteProjectDialog().submit();
        client.projects().projectsPage().waitToLoad();
    }

}
