/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.projects;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;
import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPage;

public class ProjectsPage extends BasicPage<ProjectsPage, ProjectsPageValidator> {

    private final By ADD_PROJECT_BUTTON = By
            .cssSelector(".toolbar button.btn.btn-link[allownavigation]");
    private final By CARD_CONTEXT_MENU_BUTTON = By
            .cssSelector(".btn.btn-sm.btn-link.dropdown-toggle");
    private final By CARD_DELETE_BUTTON = By
            .cssSelector(".card-actions.dropdown .dropdown-item:nth-child(2)");
    private final By DELETE_PROJECT_CONFIRMATION_BUTTON = By
            .cssSelector(".modal-dialog .btn.btn-danger");
    private final String PROJECT_CARD_BY_NAME_SELECTOR = "html/body/my-app/clr-main-container/div/app-administration/div/app-projects/grid-view/div[3]/div/span/card/div/div[1]/div/text()[normalize-space() = \"%s\"]/../../..";

    private ProjectsPageValidator validator;
    private DeleteProjectValidator deleteProjectValidator;
    private ConfigureProjectPage editProjectPage;
    private AddProjectModalDialogue addProjectModalDialogue;

    public AddProjectModalDialogue addProject() {
        LOG.info("Adding project");
        $(ADD_PROJECT_BUTTON).click();
        waitForElementToStopMoving(GlobalSelectors.MODAL_CONTENT);
        if (Objects.isNull(addProjectModalDialogue)) {
            addProjectModalDialogue = new AddProjectModalDialogue();
        }
        return addProjectModalDialogue;
    }

    public ConfigureProjectPage configureProject(String name) {
        LOG.info(String.format("Configuring project with name: [%s]", name));
        waitForElementToStopMoving(getProjectCardSelector(name)).click();
        if (Objects.isNull(editProjectPage)) {
            editProjectPage = new ConfigureProjectPage(new PageProxy(this));
        }
        editProjectPage.waitToLoad();
        return editProjectPage;
    }

    public DeleteProjectValidator deleteProject(String name) {
        LOG.info(String.format("Deleting project with name: [%s]", name));
        SelenideElement card = waitForElementToStopMoving(getProjectCardSelector(name));
        card.$(CARD_CONTEXT_MENU_BUTTON).click();
        card.$(CARD_DELETE_BUTTON).click();
        waitForElementToStopMoving(DELETE_PROJECT_CONFIRMATION_BUTTON).click();
        if (Objects.isNull(deleteProjectValidator)) {
            deleteProjectValidator = new DeleteProjectValidator();
        }
        return deleteProjectValidator;
    }

    By getProjectCardSelector(String name) {
        return By.xpath(String.format(PROJECT_CARD_BY_NAME_SELECTOR, name));
    }

    @Override
    public ProjectsPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ProjectsPageValidator(this);
        }
        return validator;
    }

    public static class DeleteProjectValidator extends BasicClass
            implements FailableActionValidator {

        private final By ERROR_MESSAGE = By.cssSelector(".modal-content .alert-text");
        private final By CANCEL_BUTTON = By.cssSelector(".modal-content .btn.btn-outline");

        @Override
        public void expectSuccess() {
            $(GlobalSelectors.MODAL_BACKDROP).should(Condition.disappear);
            waitForSpinner();
        }

        @Override
        public void expectFailure() {
            $(ERROR_MESSAGE).should(Condition.appear);
            $(CANCEL_BUTTON).click();
            $(GlobalSelectors.MODAL_BACKDROP).should(Condition.disappear);
        }

    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForSpinner();
    }

    @Override
    public ProjectsPage getThis() {
        return this;
    }

}
