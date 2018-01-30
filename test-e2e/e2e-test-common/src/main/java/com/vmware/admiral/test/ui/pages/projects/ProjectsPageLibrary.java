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

package com.vmware.admiral.test.ui.pages.projects;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;
import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPage;
import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPageLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPageValidator;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialog;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialogLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialogValidator;
import com.vmware.admiral.test.ui.pages.projects.configure.members.MembersTab;
import com.vmware.admiral.test.ui.pages.projects.configure.members.MembersTabLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.members.MembersTabValidator;

public class ProjectsPageLibrary extends PageLibrary {

    private ProjectsPage projectsPage;
    private AddProjectModalDialog addProjectDialog;
    private DeleteProjectModalDialog deleteProjectDialog;

    private ConfigureProjectPage configureProject;
    private MembersTab membersTab;
    private AddMemberModalDialog addMemberDialog;

    public ProjectsPage projectsPage() {
        if (Objects.isNull(projectsPage)) {
            ProjectsPageLocators locators = new ProjectsPageLocators();
            ProjectsPageValidator validator = new ProjectsPageValidator(getFrameLocators(),
                    locators);
            projectsPage = new ProjectsPage(getFrameLocators(), validator, locators);
        }
        return projectsPage;
    }

    public AddProjectModalDialog addProjectDialog() {
        if (Objects.isNull(addProjectDialog)) {
            AddProjectModalDialogLocators locators = new AddProjectModalDialogLocators();
            AddProjectModalDialogValidator validator = new AddProjectModalDialogValidator(
                    getFrameLocators(), locators);
            addProjectDialog = new AddProjectModalDialog(getFrameLocators(), validator, locators);
        }
        return addProjectDialog;
    }

    public DeleteProjectModalDialog deleteProjectDialog() {
        if (Objects.isNull(deleteProjectDialog)) {
            DeleteProjectModalDialogLocators locators = new DeleteProjectModalDialogLocators();
            DeleteProjectModalDialogValidator validator = new DeleteProjectModalDialogValidator(
                    getFrameLocators(), locators);
            deleteProjectDialog = new DeleteProjectModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return deleteProjectDialog;
    }

    public ConfigureProjectPage configureProjectPage() {
        if (Objects.isNull(configureProject)) {
            ConfigureProjectPageLocators locators = new ConfigureProjectPageLocators();
            ConfigureProjectPageValidator validator = new ConfigureProjectPageValidator(
                    getFrameLocators(), locators);
            configureProject = new ConfigureProjectPage(getFrameLocators(), validator, locators);
        }
        return configureProject;
    }

    public MembersTab membersTab() {
        if (Objects.isNull(membersTab)) {
            MembersTabLocators locators = new MembersTabLocators();
            MembersTabValidator validator = new MembersTabValidator(getFrameLocators(), locators);
            membersTab = new MembersTab(getFrameLocators(), validator, locators);
        }
        return membersTab;
    }

    public AddMemberModalDialog addMemberDialog() {
        if (Objects.isNull(addMemberDialog)) {
            AddMemberModalDialogLocators locators = new AddMemberModalDialogLocators();
            AddMemberModalDialogValidator validator = new AddMemberModalDialogValidator(
                    getFrameLocators(), locators);
            addMemberDialog = new AddMemberModalDialog(getFrameLocators(), validator, locators);
        }
        return addMemberDialog;
    }

    @Override
    protected By[] getFrameLocators() {
        return null;
    }

}
