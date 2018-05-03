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

import { Component, OnInit } from "@angular/core";
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from "../../../../../utils/document.service";
import { Utils } from "../../../../../utils/utils";
import { Links } from "../../../../../utils/links";
import * as I18n from 'i18next';
import {formatUtils} from "admiral-ui-common";
import {Constants} from "../../../../../utils/constants";
import {FT} from "../../../../../utils/ft";

@Component({
    selector: 'app-kubernetes-cluster-add-external',
    templateUrl: './kubernetes-cluster-add-external.component.html',
    styleUrls: ['./kubernetes-cluster-add-external.component.scss']
})
/**
 * View for adding external kubernetes clusters.
 */
export class KubernetesClusterAddExternalComponent implements OnInit {
    credentials: any[];
    projectLink: string;

    showCertificateWarning: boolean;
    certificate: any;

    isSaving: boolean;
    alertMessage: string;

    private sub: any;

    clusterForm = new FormGroup({
        name: new FormControl('', Validators.required),
        url: new FormControl('', Validators.required),
        credentials: new FormControl(''),
        description: new FormControl('')
    });

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(private router: Router, private route: ActivatedRoute, private service: DocumentService) {}

    get urlRequiredTextKey() {
        return 'kubernetes.clusters.edit.urlRequired'
    }

    ngOnInit(): void {
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

    ngAfterViewInit() {
        this.showCertificateWarning = false;
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

    cancelAdding() {
        let path = ['../'];
        this.router.navigate(path, { relativeTo: this.route });
    }

    saveCluster() {
        this.createCluster(false);
    }

    private createCluster(certificateAccepted: boolean) {
        if (this.clusterForm.valid) {
            this.isSaving = true;

            let formInput = this.clusterForm.value;
            let clusterName = formInput.name && formatUtils.escapeHtml(formInput.name);
            let hostState = {
                'address': formInput.url,
                'customProperties': {
                    '__adapterDockerType': 'API',
                    '__containerHostType': 'KUBERNETES',
                    '__clusterName': clusterName
                }
            };

            if (formInput.credentials) {
                hostState.customProperties['__authCredentialsLink'] = formInput.credentials.documentSelfLink;
            }

            if (formInput.description) {
                hostState.customProperties['__clusterDetails'] = formInput.description;
            }

            let hostSpec = {
                'hostState': hostState,
                'acceptCertificate': certificateAccepted
            };
            this.service.post(Links.CLUSTERS, hostSpec, this.projectLink).then((response) => {
                if (response.certificate) {
                    this.certificate = response;
                    this.showCertificateWarning = true;
                } else {
                    this.isSaving = false;
                    this.cancelAdding();
                }
            }).catch(error => {
                this.isSaving = false;
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }

    cancelCreateCluster() {
        this.showCertificateWarning = false;
        this.isSaving = false;
    }

    acceptCertificate() {
        this.showCertificateWarning = false;

        this.createCluster(true);
    }

    resetAlert() {
        this.alertMessage = null;
    }
}
