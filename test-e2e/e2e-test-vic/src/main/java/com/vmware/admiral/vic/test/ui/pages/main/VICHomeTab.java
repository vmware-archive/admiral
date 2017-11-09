package com.vmware.admiral.vic.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.HomeTab;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;
import com.vmware.admiral.test.ui.pages.main.HomeTabValidator;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab.VICHomeTabValidator;
import com.vmware.admiral.vic.test.ui.pages.projectrepos.ProjectRepositoriesPage;

public class VICHomeTab extends HomeTab<VICHomeTab, VICHomeTabValidator> {

    public static final By PROJECT_REPOSITORIES_BUTTON = By.cssSelector(
            HomeTabSelectors.LEFT_MENU_BASE + " .nav-link[href*=project-repositories]");

    private VICHomeTabValidator validator;

    private ProjectRepositoriesPage projectRepositoriesPage;

    public ProjectRepositoriesPage navigateToProjectRepositoriesPage() {
        if (clickIfNotActive(PROJECT_REPOSITORIES_BUTTON)) {
            $(By.cssSelector(".datagrid-spinner")).should(Condition.appear)
                    .should(Condition.disappear);
        }
        if (Objects.isNull(projectRepositoriesPage)) {
            projectRepositoriesPage = new ProjectRepositoriesPage();
        }
        return projectRepositoriesPage;
    }

    @Override
    public VICHomeTabValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new VICHomeTabValidator(this);
        }
        return validator;
    }

    public static class VICHomeTabValidator extends HomeTabValidator<VICHomeTabValidator> {

        VICHomeTabValidator(VICHomeTab page) {
            super(page);
        }

        public VICHomeTabValidator validateProjectRepositoriesAvailable() {
            $(PROJECT_REPOSITORIES_BUTTON).shouldBe(Condition.visible);
            return this;
        }

        public VICHomeTabValidator validateProjectRepositoriesNotAvailable() {
            $(PROJECT_REPOSITORIES_BUTTON).shouldNotBe(Condition.visible);
            return this;
        }

        public VICHomeTabValidator validateAllHomeTabsAreAvailable() {
            validateApplicationsAvailable();
            validateContainersAvailable();
            validateNetworksAvailable();
            validateVolumesAvailable();
            validateTemplatesAvailable();
            validateProjectRepositoriesAvailable();
            validatePublicRepositoriesAvailable();
            validateContainerHostsAvailable();
            return this;
        }

        @Override
        public VICHomeTabValidator getThis() {
            return this;
        }

    }

    @Override
    public VICHomeTab getThis() {
        return this;
    }
}
