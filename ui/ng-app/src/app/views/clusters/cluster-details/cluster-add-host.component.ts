/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, Input, Output, EventEmitter, AfterViewInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from '../../../utils/document.service';
import { Constants } from '../../../utils/constants';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
    selector: 'app-cluster-add-host',
    templateUrl: './cluster-add-host.component.html',
    styleUrls: ['./cluster-add-host.component.scss']
})
/**
 * Add Hosts to Cluster dialog.
 */
export class ClusterAddHostComponent implements AfterViewInit {
    @Input() cluster: any;
    @Input() visible: boolean;
    @Input() projectLink: string;

    isAddingHost: boolean;
    credentials: any[] = [];
    showCertificateWarning: boolean;
    certificate: any;
    alertMessage: string;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    addHostToClusterForm = new FormGroup({
        address: new FormControl('', Validators.required),
        credentials: new FormControl(''),
        publicAddress: new FormControl('')
    });

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(private documentService: DocumentService) { }

    ngAfterViewInit() {
        setTimeout(() => {
            this.showCertificateWarning = false;
        });

        this.documentService.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                                    .filter(c => !Utils.areSystemScopedCredentials(c))
                                    .map(this.toCredentialViewModel);
        });
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled();
      }

    clearView() {
        this.resetAlert();
        this.isAddingHost = false;
        this.addHostToClusterForm.reset();
        this.addHostToClusterForm.markAsPristine();
    }

    resetAlert() {
        this.alertMessage = null;
    }

    toCredentialViewModel(credential) {
        let credentialViewModel:any = {};

        credentialViewModel.documentSelfLink = credential.documentSelfLink;
        credentialViewModel.name = credential.customProperties
                                        ? credential.customProperties.__authCredentialsName : '';
        if (!credentialViewModel.name) {
            credentialViewModel.name = credential.documentId;
        }

        return credentialViewModel;
    }

    declineCertificate() {
        this.showCertificateWarning = false;
        this.isAddingHost = false;
    }

    acceptCertificate() {
        this.showCertificateWarning = false;
        this.addHost(true);
    }

    addHostCanceled() {
        this.clearView();
        this.onCancel.emit(null);
    }

    addHost(certificateAccepted: boolean) {
        if (this.addHostToClusterForm.valid) {
            this.isAddingHost = true;

            let formInput = this.addHostToClusterForm.value;
            let hostState = {
                'address': formInput.address,
                'customProperties': {
                    '__containerHostType': 'DOCKER',
                    '__adapterDockerType': 'API'
                }
            };

            if (formInput.credentials) {
                hostState.customProperties['__authCredentialsLink'] =
                                                            formInput.credentials.documentSelfLink;
            }

            if (formInput.publicAddress) {
                hostState.customProperties[Constants.hosts.customProperties.publicAddress] =
                                                                            formInput.publicAddress;
            }

            let hostSpec = {
                'hostState': hostState,
                'acceptCertificate': certificateAccepted
            };
            let clusterHostsLink = this.cluster.documentSelfLink + '/hosts';

            this.documentService.post(clusterHostsLink, hostSpec, this.projectLink)
                                    .then((response) => {
                if (response && response.certificate) {

                    this.certificate = response;
                    this.showCertificateWarning = true;
                } else {

                    this.clearView();
                    this.onChange.emit(null);
                }
            }).catch(error => {

                this.isAddingHost = false;
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }
}
