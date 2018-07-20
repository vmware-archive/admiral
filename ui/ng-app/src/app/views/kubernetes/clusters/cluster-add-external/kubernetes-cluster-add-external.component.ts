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
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from "../../../../utils/project.service";
import { Links } from "../../../../utils/links";
import { Utils } from "../../../../utils/utils";

import * as I18n from 'i18next';
import { formatUtils } from "admiral-ui-common";

@Component({
    selector: 'app-kubernetes-cluster-add-external',
    templateUrl: './kubernetes-cluster-add-external.component.html',
    styleUrls: ['./kubernetes-cluster-add-external.component.scss']
})
/**
 * View for adding external clusters.
 */
export class KubernetesClusterAddExternalComponent implements OnInit {
    credentials: any[];
    // actions
    isSaving: boolean;
    // certificate
    showCertificateWarning: boolean;
    certificate: any;

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

    constructor(private router: Router, private route: ActivatedRoute,
                private service: DocumentService, private projectService: ProjectService,
                private errorService: ErrorService) {
    }

    ngOnInit(): void {
        this.populateCredentials();
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
            .map(Utils.toCredentialViewModel);
        }).catch((e) => {
            console.log('Credentials retrieval failed', e);
        });
    }

    save() {
        this.createCluster(false);
    }

    cancel() {
        this.goBack();
    }

    private createCluster(certificateAccepted: boolean) {
        if (this.clusterForm.valid) {
            let formValues = this.clusterForm.value;

            let hostState = {
                'address': formValues.url,
                'customProperties': {
                    '__adapterDockerType': 'API',
                    '__containerHostType': 'KUBERNETES',
                    '__clusterName': formValues.name && formatUtils.escapeHtml(formValues.name)
                }
            };

            if (formValues.credentials) {
                hostState.customProperties['__authCredentialsLink'] =
                                                            formValues.credentials.documentSelfLink;
            }

            if (formValues.description) {
                hostState.customProperties['__clusterDetails'] = formValues.description;
            }

            let clusterSpec = {
                'hostState': hostState,
                'acceptCertificate': certificateAccepted
            };

            this.isSaving = true;

            this.service.post(Links.CLUSTERS, clusterSpec).then((response) => {

                if (response.certificate) {
                    // certificate has to be accepted by the user
                    this.certificate = response;
                    this.showCertificateWarning = true;
                } else {
                    this.isSaving = false;
                    this.goBack();
                }
            }).catch(error => {
                this.isSaving = false;

                console.error(error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            });
        }
    }

    acceptCertificate() {
        this.showCertificateWarning = false;

        this.createCluster(true);
    }

    cancelAcceptCertificate() {
        this.showCertificateWarning = false;
        this.isSaving = false;

        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }
}
