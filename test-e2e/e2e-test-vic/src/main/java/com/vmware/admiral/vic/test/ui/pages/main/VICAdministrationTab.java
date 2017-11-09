package com.vmware.admiral.vic.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.AdministrationTab;
import com.vmware.admiral.test.ui.pages.main.AdministrationTabValidator;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPage;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab.VICAdministrationTabValidator;

public class VICAdministrationTab
        extends AdministrationTab<VICAdministrationTab, VICAdministrationTabValidator> {

    private static By CONFIGURATION_BUTTON = By
            .cssSelector(".sidenav-content .nav-link[routerlink*=configuration]");

    private ConfigurationPage configurationPage;
    private VICAdministrationTabValidator validator;

    public ConfigurationPage navigateToConfigurationPage() {
        clickIfNotActive(CONFIGURATION_BUTTON);
        if (Objects.isNull(configurationPage)) {
            configurationPage = new ConfigurationPage();
        }
        return configurationPage;
    }

    @Override
    public VICAdministrationTabValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new VICAdministrationTabValidator();
        }
        return validator;
    }

    public static class VICAdministrationTabValidator
            extends AdministrationTabValidator<VICAdministrationTabValidator> {

        public VICAdministrationTabValidator validateConfigurationAvailable() {
            $(CONFIGURATION_BUTTON).shouldBe(Condition.visible);
            return this;
        }

        public VICAdministrationTabValidator validateConfigurationNotAvailable() {
            $(CONFIGURATION_BUTTON).shouldNotBe(Condition.visible);
            return this;
        }

        public VICAdministrationTabValidator validateAllAdministrationTabsAreAvailable() {
            validateIdentityManagementAvailable();
            validateProjectsAvailable();
            validateRegistriesAvailable();
            validateConfigurationAvailable();
            validateLogsAvailable();
            return this;
        }

        @Override
        public VICAdministrationTabValidator getThis() {
            return this;
        }
    }

    @Override
    public VICAdministrationTab getThis() {
        return this;
    }
}
