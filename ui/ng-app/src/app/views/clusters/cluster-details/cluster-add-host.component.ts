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

import { Component, Input, Output, EventEmitter, AfterViewInit, OnInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from '../../../utils/document.service';
import { Constants } from '../../../utils/constants';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";

@Component({
    selector: 'app-cluster-add-host',
    templateUrl: './cluster-add-host.component.html',
    styleUrls: ['./cluster-add-host.component.scss']
})
/**
 * Add Hosts to Cluster dialog.
 */
export class ClusterAddHostComponent implements AfterViewInit, OnInit {
    @Input() cluster: any;
    @Input() visible: boolean;
    @Input() projectLink: string;
    @Input() deploymentPolicies: any[] = [];

    isAddingHost: boolean;

    credentialsLoading: boolean = false;
    credentials: any[] = [];
    selectedCredential: any;

    deploymentPolicySelection: any;

    showCertificateWarning: boolean;
    certificate: any;

    alertType: string;
    alertMessage: string;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    addHostToClusterForm = new FormGroup({
        address: new FormControl('', Validators.required),
        name: new FormControl(''),
        publicAddress: new FormControl(''),
        deploymentPolicy: new FormControl('')
    });

    constructor(private documentService: DocumentService) { }

    ngAfterViewInit() {
        setTimeout(() => {
            this.showCertificateWarning = false;
        });
    }

    ngOnInit() {
        this.populateCredentials();
    }

    populateCredentials() {
        this.credentialsLoading = true;
        this.documentService.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentialsLoading = false;

            this.credentials = credentials.documents.filter(c => !Utils.areSystemScopedCredentials(c))
                                                        .map(Utils.toCredentialViewModel);
        }).catch((error) => {
            console.error('Credentials retrieval failed', error);
            this.credentialsLoading = false;

            this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
        });
    }

    get isApplicationEmbedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled();
    }

    clearView() {
        this.resetAlert();
        this.isAddingHost = false;
        this.addHostToClusterForm.reset();
        this.addHostToClusterForm.markAsPristine();
        this.selectedCredential = null;
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

    onCredentialsSelection(selectedCredential) {
        this.selectedCredential = selectedCredential;
    }

    addHost(certificateAccepted: boolean) {
        if (this.addHostToClusterForm.valid) {
            this.isAddingHost = true;

            let formData = this.addHostToClusterForm.value;
            let hostState = {
                'address': formData.address,
                'customProperties': {
                    '__containerHostType': 'DOCKER',
                    '__adapterDockerType': 'API'
                }
            };

            if (formData.name) {
                hostState.customProperties['__hostAlias'] = formData.name;
            }

            if (this.selectedCredential) {
                hostState.customProperties['__authCredentialsLink'] = this.selectedCredential;
            }

            if (formData.publicAddress) {
                hostState.customProperties[Constants.hosts.customProperties.publicAddress] =
                                                                            formData.publicAddress;
            }

            if (this.deploymentPolicySelection) {
                hostState.customProperties[Constants.hosts.customProperties.deploymentPolicyLink] =
                        this.deploymentPolicySelection;
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
                console.error('Failed to add host to cluster', error);
                this.isAddingHost = false;

                this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
            });
        }
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
