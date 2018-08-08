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

package com.vmware.admiral.test.ui.pages.identity;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class IdentityManagementPageLibrary extends PageLibrary {

    private final By[] INNER_FRAME_LOCATORS;

    public IdentityManagementPageLibrary(By[] iframeLocators, By[] innerFrameLocators) {
        super(iframeLocators);
        if (Objects.nonNull(iframeLocators)) {
            this.INNER_FRAME_LOCATORS = innerFrameLocators.clone();
        } else {
            this.INNER_FRAME_LOCATORS = null;
        }
    }

    private IdentityManagementPage identityPage;
    private UsersAndGroupsTab usersTab;

    private CertificatesTab certificatesTab;
    private AddCertificateForm addCertificateForm;

    private CredentialsTab credentialsTab;
    private NewCredentialForm newCredentialForm;
    private UsernameCredentialForm usernameCredentialForm;
    private CertificateCredentialForm certificateCredentialForm;

    public IdentityManagementPage identityPage() {
        if (Objects.isNull(identityPage)) {
            IdentityManagementPageLocators locators = new IdentityManagementPageLocators();
            IdentityManagementPageValidator validator = new IdentityManagementPageValidator(
                    getFrameLocators(), locators);
            identityPage = new IdentityManagementPage(getFrameLocators(), validator, locators);
        }
        return identityPage;
    }

    public CredentialsTab credentialsTab() {
        if (Objects.isNull(credentialsTab)) {
            CredentialsTabLocators locators = new CredentialsTabLocators();
            CredentialsTabValidator validator = new CredentialsTabValidator(
                    INNER_FRAME_LOCATORS, locators);
            credentialsTab = new CredentialsTab(INNER_FRAME_LOCATORS, validator, locators);
        }
        return credentialsTab;
    }

    public NewCredentialForm newCredentialForm() {
        if (Objects.isNull(newCredentialForm)) {
            NewCredentialFormLocators locators = new NewCredentialFormLocators();
            newCredentialForm = new NewCredentialForm(INNER_FRAME_LOCATORS, locators);
        }
        return newCredentialForm;
    }

    public UsernameCredentialForm usernameCredentialForm() {
        if (Objects.isNull(usernameCredentialForm)) {
            UsernameCredentialFormLocators locators = new UsernameCredentialFormLocators();
            usernameCredentialForm = new UsernameCredentialForm(INNER_FRAME_LOCATORS, locators);
        }
        return usernameCredentialForm;
    }

    public CertificateCredentialForm certificateCredentialForm() {
        if (Objects.isNull(certificateCredentialForm)) {
            CertificateCredentialFormLocators locators = new CertificateCredentialFormLocators();
            certificateCredentialForm = new CertificateCredentialForm(INNER_FRAME_LOCATORS,
                    locators);
        }
        return certificateCredentialForm;
    }

    public UsersAndGroupsTab usersTab() {
        if (Objects.isNull(usersTab)) {
            UsersAndGroupsTabLocators locators = new UsersAndGroupsTabLocators();
            UsersAndGroupsTabValidator validator = new UsersAndGroupsTabValidator(
                    getFrameLocators(), locators);
            usersTab = new UsersAndGroupsTab(getFrameLocators(), validator, locators);
        }
        return usersTab;
    }

    public CertificatesTab certificatesTab() {
        if (Objects.isNull(certificatesTab)) {
            CertificatesTabLocators locators = new CertificatesTabLocators();
            CertificatesTabValidator validator = new CertificatesTabValidator(INNER_FRAME_LOCATORS,
                    locators);
            certificatesTab = new CertificatesTab(INNER_FRAME_LOCATORS, validator, locators);
        }
        return certificatesTab;
    }

    public AddCertificateForm addCertificateForm() {
        if (Objects.isNull(addCertificateForm)) {
            AddCertificateFormLocators locators = new AddCertificateFormLocators();
            addCertificateForm = new AddCertificateForm(INNER_FRAME_LOCATORS, locators);
        }
        return addCertificateForm;
    }

}
