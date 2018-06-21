/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Constants } from '../../../utils/constants';
import { DocumentService } from '../../../utils/document.service';
import { ProjectService } from "../../../utils/project.service";
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";

import * as I18n from 'i18next';
import { formatUtils } from 'admiral-ui-common';

@Component({
  selector: 'app-cluster-create',
  templateUrl: './cluster-create.component.html',
  styleUrls: ['./cluster-create.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
/**
 * View for cluster creation.
 */
export class ClusterCreateComponent implements OnInit {
    credentials: any[];

    showCertificateWarning: boolean;
    certificate: any;

    isSaving: boolean;

    alertType: string;
    alertMessage: string;

    private isSingleHostCluster: boolean = false;

    clusterForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl(''),
        type: new FormControl('VCH'),
        url: new FormControl('', Validators.required),
        publicAddress: new FormControl(''),
        credentials: new FormControl('')
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
                private documentService: DocumentService, private projectService: ProjectService) {
        //
    }

    get title() {
        return FT.isVic() ? 'clusters.edit.titleNewVic' : 'clusters.edit.titleNew';
    }

    get urlRequiredTextKey() {
        return FT.isVic() ? 'clusters.edit.urlRequiredVic' : 'clusters.edit.urlRequired';
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled() && this.isSingleHostCluster;
    }

    ngOnInit() {
        this.populateCredentials();
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.documentService.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                                    .filter(c => !Utils.areSystemScopedCredentials(c))
                                        .map(Utils.toCredentialViewModel);
        }).catch((error) => {
            console.error('Credentials retrieval failed', error);

            this.showAlertMessage(Constants.alert.type.DANGER,
                                    Utils.getErrorMessage(error)._generic);
        });
    }

    save() {
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
                  '__containerHostType': formInput.type,
                  '__adapterDockerType': 'API',
                  '__clusterName': clusterName
                }
            };

            if (formInput.credentials) {
                hostState.customProperties['__authCredentialsLink']
                                                        = formInput.credentials.documentSelfLink;
            }

            if (formInput.description) {
                hostState.customProperties['__clusterDetails'] = formInput.description;
            }

            if (formInput.publicAddress) {
                hostState.customProperties[Constants.hosts.customProperties.publicAddress]
                                                        = formInput.publicAddress;
            }

            let hostSpec = {
                'hostState': hostState,
                'acceptCertificate': certificateAccepted
            };

            this.documentService.post(Links.CLUSTERS, hostSpec,
                                      this.projectService.getSelectedProject().documentSelfLink)
                .then((response) => {

                if (response.certificate) {
                    // certificate to be accepted by the user
                  this.certificate = response;
                  this.showCertificateWarning = true;
                } else {
                  this.isSaving = false;
                  this.goBack();
                }
            }).catch(error => {
                this.isSaving = false;

                this.showAlertMessage(Constants.alert.type.DANGER,
                                        Utils.getErrorMessage(error)._generic);
            });
        }
    }

    cancel() {
        this.showCertificateWarning = false;
        this.isSaving = false;

        this.goBack();
    }

    acceptCertificate() {
        this.showCertificateWarning = false;

        this.createCluster(true);
    }

    cancelAcceptCertificate() {
        this.showCertificateWarning = false;
        this.isSaving = false;

        this.showAlertMessage(Constants.alert.type.WARNING,
                        'Cannot proceed. Certificate is not accepted.');
    }

    goBack() {
        let path: any[] = Utils.getPathUp(this.router.url, 'infra');

        this.router.navigate(path, { relativeTo: this.route });
    }

    private showAlertMessage(messageType, message) {
        this.alertType = messageType;
        this.alertMessage = message;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
