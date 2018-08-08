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

import com.vmware.admiral.test.ui.pages.common.CertificateModalDialog;
import com.vmware.admiral.test.ui.pages.common.CertificateModalDialogLocators;
import com.vmware.admiral.test.ui.pages.common.CertificateModalDialogValidator;
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
import com.vmware.admiral.test.ui.pages.projects.configure.registries.AddProjectRegistryForm;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.AddProjectRegistryFormLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.AddProjectRegistryFormValidator;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.DeleteRegistryModalDialog;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.DeleteRegistryModalDialogLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.DeleteRegistryModalDialogValidator;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.EditProjectRegistryForm;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.EditProjectRegistryFormValidator;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.ProjectRegistriesTab;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.ProjectRegistriesTabLocators;
import com.vmware.admiral.test.ui.pages.projects.configure.registries.ProjectRegistriesTabValidator;

public class ProjectsPageLibrary extends PageLibrary {

    public ProjectsPageLibrary(By[] iframeLocators) {
        super(iframeLocators);
    }

    private ProjectsPage projectsPage;
    private AddProjectModalDialog addProjectDialog;
    private DeleteProjectModalDialog deleteProjectDialog;
    private CertificateModalDialog certificateModalDialog;
    private ProjectRegistriesTab registriesTab;
    private AddProjectRegistryForm addRegistryForm;
    private EditProjectRegistryForm editRegistryForm;
    private DeleteRegistryModalDialog deleteRegistryDialog;

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

    public ProjectRegistriesTab projectRegistriesTab() {
        if (Objects.isNull(registriesTab)) {
            ProjectRegistriesTabLocators locators = new ProjectRegistriesTabLocators();
            ProjectRegistriesTabValidator validator = new ProjectRegistriesTabValidator(
                    getFrameLocators(), locators);
            registriesTab = new ProjectRegistriesTab(getFrameLocators(), validator, locators);
        }
        return registriesTab;
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

    public AddProjectRegistryForm addRegistryForm() {
        if (Objects.isNull(addRegistryForm)) {
            AddProjectRegistryFormLocators locators = new AddProjectRegistryFormLocators();
            AddProjectRegistryFormValidator validator = new AddProjectRegistryFormValidator(
                    getFrameLocators(), locators);
            addRegistryForm = new AddProjectRegistryForm(getFrameLocators(), validator, locators);
        }
        return addRegistryForm;
    }

    public EditProjectRegistryForm editRegistryForm() {
        if (Objects.isNull(editRegistryForm)) {
            AddProjectRegistryFormLocators locators = new AddProjectRegistryFormLocators();
            EditProjectRegistryFormValidator validator = new EditProjectRegistryFormValidator(
                    getFrameLocators(), locators);
            editRegistryForm = new EditProjectRegistryForm(getFrameLocators(), validator, locators);
        }
        return editRegistryForm;
    }

    public DeleteRegistryModalDialog deleteRegistryDialog() {
        if (Objects.isNull(deleteRegistryDialog)) {
            DeleteRegistryModalDialogLocators locators = new DeleteRegistryModalDialogLocators();
            DeleteRegistryModalDialogValidator validator = new DeleteRegistryModalDialogValidator(
                    getFrameLocators(), locators);
            deleteRegistryDialog = new DeleteRegistryModalDialog(getFrameLocators(), validator,
                    locators);
        }
        return deleteRegistryDialog;
    }

}
