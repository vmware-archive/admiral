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

import { Component, AfterViewInit, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from "@angular/router";

import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from "../../../utils/document.service";
import { ErrorService } from "../../../utils/error.service";
import { Links } from "../../../utils/links";
import { FormControl, FormGroup } from "@angular/forms";
import { Utils } from "../../../utils/utils";
import { formatUtils } from 'admiral-ui-common';
import * as I18n from 'i18next';
import { Constants } from '../../../utils/constants';

@Component({
    selector: 'app-project-registry-details',
    templateUrl: './project-registry-details.component.html',
    styleUrls: ['./project-registry-details.component.scss']
})

/**
 * View for create/edit project registry.
 */
export class ProjectRegistryDetailsComponent extends BaseDetailsComponent implements OnInit {
    editMode: boolean = false;
    credentials: any[];
    projectLink: string;
    registryLink: string;

    showCertificateWarning: boolean;
    certificate: any;

    alertType: string;

    private sub: any;
    isSaving: boolean;

    projectRegistryDetailsForm = new FormGroup({
        name: new FormControl(''),
        address: new FormControl(''),
        credentials: new FormControl('')
    });

    isSavingRegistry: boolean = false;
    isTestingConnection: boolean = false;

    alertMessage: string;

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', { ns: 'base' })
    } as I18n.TranslationOptions);

    constructor(private router: Router, route: ActivatedRoute, documentService: DocumentService,
        errorService: ErrorService) {
        super(route, documentService, Links.REGISTRIES, errorService);
    }

    ngOnInit() {
        super.ngOnInit();
        this.populateCredentials(null);
    }

    populateCredentials(authCredentialsLink) {
        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                .filter(c => !Utils.areSystemScopedCredentials(c))
                .map(this.toCredentialViewModel);
                if (authCredentialsLink) {
                    this.preselectCredential(authCredentialsLink);
                }
        }).catch((e) => {
            console.log('Credentials retrieval failed', e);
        });
    }

    preselectCredential(authCredentialsLink) {
        if (authCredentialsLink) {
            var credItem = this.credentials
                .filter((c) => c.documentSelfLink === authCredentialsLink);
            if (credItem.length > 0) {
                this.projectRegistryDetailsForm.get('credentials').setValue(credItem[0]);
            }
        }
    }

    toCredentialViewModel(credential) {
        let credentialsViewModel: any = {};

        credentialsViewModel.documentSelfLink = credential.documentSelfLink;
        credentialsViewModel.name = credential.customProperties
            ? credential.customProperties.__authCredentialsName : '';
        if (!credentialsViewModel.name) {
            credentialsViewModel.name = credential.documentId;
        }

        return credentialsViewModel;
    }

    protected entityInitialized() {
        let authCredentialsLink;
        if (this.entity) {
            // edit mode
            this.editMode = true;
            this.projectRegistryDetailsForm.get('name').setValue(this.entity.name);
            this.projectRegistryDetailsForm.get('address').setValue(this.entity.address);
            authCredentialsLink = this.entity.authCredentialsLink;
        }

        this.populateCredentials(authCredentialsLink);
    }

    resetAlert() {
        this.alertMessage = null;
    }

    private save(acceptCert: boolean = false) {
        this.isSavingRegistry = true;
        let registrySpec = this.getRegistrySpec(acceptCert);

        this.service.put(Links.REGISTRY_SPEC, registrySpec, this.projectLink).then((response) => {
            if (!this.isCertificateResponse(response)) {
                this.isSavingRegistry = false;
                this.router.navigate(['..'], { relativeTo: this.route });
            }
        }).catch(error => {
            this.isSavingRegistry = false;
            this.showErrorMessage(error);
        });
    }

    private update(acceptCert: boolean = false) {
        this.isSavingRegistry = true;
        let formInput = this.projectRegistryDetailsForm.value;
        let registryName = formInput.name && formatUtils.escapeHtml(formInput.name);

        this.entity.name = formInput.name && formatUtils.escapeHtml(formInput.name);
        this.entity.address = formInput.address;
        this.entity.endpointType = 'container.docker.registry';
        this.entity.authCredentialsLink = formInput.credentials.documentSelfLink;

        let registrySpec = {
            'hostState': this.entity,
            'acceptCertificate': acceptCert
        };

        this.service.put(Links.REGISTRY_SPEC, registrySpec, this.projectLink).then((response) => {
            if (!this.isCertificateResponse(response)) {
                this.isSavingRegistry = false;
                this.router.navigate(['../../'], { relativeTo: this.route });
            }
        }).catch(error => {
            this.isSavingRegistry = false;
            this.showErrorMessage(error);
        });
    }

    private cancel() {
        if (this.editMode) {
            this.router.navigate(['../../'], { relativeTo: this.route });
        } else {
            this.router.navigate(['..'], { relativeTo: this.route });
        }
    }

    private testConnection(acceptCert: boolean = false) {
        this.isTestingConnection = true;
        let registrySpec = this.getRegistrySpec(acceptCert);

        this.service.put(Links.REGISTRY_SPEC + '?validate=true', registrySpec).then((response) => {
            if (!this.isCertificateResponse(response)) {
                this.isTestingConnection = false;
                this.alertType = Constants.alert.type.SUCCESS;
                this.alertMessage = I18n.t('hosts.verified');
            }
        }).catch(error => {
            this.isTestingConnection = false;
            this.showErrorMessage(error);
        });
    }

    private getRegistrySpec(acceptCert: boolean) {
        let formInput = this.projectRegistryDetailsForm.value;
        let registryName = formInput.name && formatUtils.escapeHtml(formInput.name);
        let registryState = {
            'address': formInput.address,
            'name': registryName,
            'endpointType': 'container.docker.registry',
            'authCredentialsLink': formInput.credentials.documentSelfLink
        };
        registryState.authCredentialsLink = formInput.credentials.documentSelfLink;
        let registrySpec = {
            'hostState': registryState,
            'acceptCertificate': acceptCert
        };
        return registrySpec;
    }

    private showErrorMessage(error) {
        this.alertType = Constants.alert.type.DANGER;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
    }

    declineCertificate() {
        this.showCertificateWarning = false;
        this.isSavingRegistry = false;
        this.isTestingConnection = false;
    }

    acceptCertificate() {
        this.showCertificateWarning = false;
        if (this.editMode && this.isSavingRegistry) {
            this.update(true);
        } else if (this.isTestingConnection) {
            this.testConnection(true);
        } else if (this.isSavingRegistry) {
            this.save(true);
        }
    }

    isCertificateResponse(response: any) {
        if (response && response.certificate) {
            this.certificate = response;
            this.showCertificateWarning = true;
            return true;
        } else {
            return false;
        }
    }
}