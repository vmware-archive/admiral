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
import { FormGroup, FormControl, Validators, AbstractControl } from "@angular/forms";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from "../../../../utils/project.service";
import { Constants } from '../../../../utils/constants';
import { Links } from "../../../../utils/links";
import { Utils } from "../../../../utils/utils";
import { CustomValidators } from "../../../../utils/validators";

@Component({
    selector: 'app-kubernetes-cluster-add-external',
    templateUrl: './kubernetes-cluster-add-external.component.html',
    styleUrls: ['./kubernetes-cluster-add-external.component.scss']
})
/**
 * View for adding external clusters.
 */
export class KubernetesClusterAddExternalComponent implements OnInit {
    // Credentials
    credentialsLoading: boolean = false;
    credentials: any[];
    selectedCredential: any;

    // actions
    isSaving: boolean;
    // certificate
    showCertificateWarning: boolean;
    certificate: any;

    alertType: string;
    alertMessage: string;

    clusterForm = new FormGroup({
        name: new FormControl('', Validators.required),
        url: new FormControl('', CustomValidators.validateUrl),
        description: new FormControl('')
    });

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

        this.credentialsLoading = true;
        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentialsLoading = false;

            this.credentials = credentials.documents
                                    .filter(c => !Utils.areSystemScopedCredentials(c))
                                    .map(Utils.toCredentialViewModel);
        }).catch((error) => {
            console.log('Credentials retrieval failed', error);
            this.credentialsLoading = false;

            this.showErrorMessage(error);
        });
    }

    add() {
        this.addCluster(false);
    }

    cancel() {
        this.goBack();
    }

    onCredentialsSelection(selectedCredential) {
        this.selectedCredential = selectedCredential;
    }

    private addCluster(certificateAccepted: boolean) {
        if (this.clusterForm.valid) {
            let formData = this.clusterForm.value;

            let hostState = {
                'address': formData.url,
                'customProperties': {
                    '__adapterDockerType': 'API',
                    '__containerHostType': 'KUBERNETES',
                    '__clusterName': Utils.escapeHtml(formData.name)
                }
            };

            if (this.selectedCredential) {
                hostState.customProperties['__authCredentialsLink'] = this.selectedCredential;
            }

            if (formData.description) {
                hostState.customProperties['__clusterDetails'] = formData.description;
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
                console.error('Failed to add cluster', error);
                this.isSaving = false;

                this.showErrorMessage(error);
            });
        }
    }

    acceptCertificate() {
        this.showCertificateWarning = false;

        this.addCluster(true);
    }

    cancelAcceptCertificate() {
        this.showCertificateWarning = false;
        this.isSaving = false;

        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }


    private showErrorMessage(error) {
        this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
    }

    private showAlertMessage(type, text) {
        this.alertType = type;
        this.alertMessage = text;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
