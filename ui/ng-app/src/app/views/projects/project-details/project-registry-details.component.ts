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

import { Component, AfterViewInit, OnInit, OnDestroy } from '@angular/core';
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
export class ProjectRegistryDetailsComponent extends BaseDetailsComponent implements OnInit, OnDestroy {
    editMode: boolean = false;
    credentials: any[];
    projectLink: string;
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
        this.populateCredentials();
    }

    ngOnDestroy() {
        this.sub.unsubscribe();
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                .filter(c => !Utils.areSystemScopedCredentials(c))
                .map(this.toCredentialViewModel);
        }).catch((e) => {
            console.log('Credentials retrieval failed', e);
        });
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
        if (this.entity) {
            // edit mode
            this.editMode = true;
        }
    }

    resetAlert() {
        this.alertMessage = null;
    }

    private save() {
        this.isSaving = true;
        let formInput = this.projectRegistryDetailsForm.value;
        let registryName = formInput.name && formatUtils.escapeHtml(formInput.name);
        let registryState = {
            'address': formInput.address,
            'name': registryName,
            'endpointType': 'container.docker.registry',
            'authCredentialsLink': formInput.credentials
        };

        let registrySpec = {
            'hostState': registryState,
            'acceptCertificate': true
        };

        this.service.put(Links.REGISTRY_SPEC, registrySpec).then((response) => {
            this.isSaving = false;
            this.router.navigate(['..'], { relativeTo: this.route });
        }).catch(error => {
            this.isSaving = false;
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    private cancel() {
        this.router.navigate(['..'], { relativeTo: this.route });
    }
}