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
        this.sub = this.route.params.subscribe(params => {
            let projectId = params['projectId'];
            if (projectId) {
                this.projectLink = Links.PROJECTS + '/' + projectId;
            }
        });
    }

    ngOnDestroy() {
        this.sub.unsubscribe();
    }

    populateCredentials(authCredentialsLink) {
        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                .filter(c => !Utils.areSystemScopedCredentials(c))
                .map(this.toCredentialViewModel);
            this.preselectCredential(authCredentialsLink);
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

    private save() {
        this.isSavingRegistry = true;
        let formInput = this.projectRegistryDetailsForm.value;
        let registryName = formInput.name && formatUtils.escapeHtml(formInput.name);
        let registryState = {
            'address': formInput.address,
            'name': registryName,
            'endpointType': 'container.docker.registry',
            'authCredentialsLink': formInput.credentials.documentSelfLink
        };

        let registrySpec = {
            'hostState': registryState,
            'acceptCertificate': true
        };

        this.service.put(Links.REGISTRY_SPEC, registrySpec, this.projectLink).then((response) => {
            this.isSavingRegistry = false;
            this.router.navigate(['..'], { relativeTo: this.route });
        }).catch(error => {
            this.isSavingRegistry = false;
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    private update() {
        this.isSavingRegistry = true;
        let formInput = this.projectRegistryDetailsForm.value;
        let registryName = formInput.name && formatUtils.escapeHtml(formInput.name);

        this.entity.name = formInput.name && formatUtils.escapeHtml(formInput.name);
        this.entity.address = formInput.address;
        this.entity.endpointType = 'container.docker.registry';
        this.entity.authCredentialsLink = formInput.documentSelfLink;

        let registrySpec = {
            'hostState': this.entity,
        };

        this.service.put(Links.REGISTRY_SPEC, registrySpec, this.projectLink).then((response) => {
            this.isSavingRegistry = false;
            this.router.navigate(['../../'], { relativeTo: this.route });
        }).catch(error => {
            this.isSavingRegistry = false;
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    private cancel() {
        if (this.editMode) {
            this.router.navigate(['../../'], { relativeTo: this.route });
        } else {
            this.router.navigate(['..'], { relativeTo: this.route });
        }
    }
}