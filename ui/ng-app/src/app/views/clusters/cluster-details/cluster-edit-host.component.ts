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

import { Component, OnInit, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { FormGroup, FormControl } from "@angular/forms";
import { DocumentService } from '../../../utils/document.service';
import { Constants } from '../../../utils/constants';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
    selector: 'app-cluster-edit-host',
    templateUrl: './cluster-edit-host.component.html',
    styleUrls: ['./cluster-edit-host.component.scss']
})
/**
 * Edit Cluster Hosts dialog.
 */
export class ClusterEditHostComponent implements OnInit, OnChanges {
    @Input() visible: boolean;
    @Input() host: any;
    @Input() deploymentPolicies: any[] = [];

    credentialsLoading: boolean = false;
    credentials: any[] = [];
    preselectedCredential:any;
    selectedCredential: any;

    deploymentPolicySelection: any;

    isSavingHost: boolean;
    isVerifyingHost: boolean;
    isHostVerified: boolean = true;

    alertMessage: string;
    alertType: string;

    projectLinks: string[] = [];
    showCertificateWarning: boolean = false;
    certificate: string;
    trustCertLink: string;
    hostUri: string;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    editHostForm = new FormGroup({
        name: new FormControl(''),
        publicAddress: new FormControl(''),
        deploymentPolicy: new FormControl('')
    });

    constructor(private documentService: DocumentService) { }

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

    ngOnChanges(changes: SimpleChanges) {
        if (this.host) {
            this.editHostForm.get('name').setValue(Utils.getHostName(this.host));

            this.preselectedCredential = Utils.getCustomPropertyValue(this.host.customProperties,
                                            '__authCredentialsLink');

            let publicAddress = Utils.getCustomPropertyValue(this.host.customProperties,
                Constants.hosts.customProperties.publicAddress) || "";
            this.editHostForm.get('publicAddress').setValue(publicAddress);

            this.deploymentPolicySelection = Utils.getCustomPropertyValue(this.host.customProperties,
                                                        Constants.hosts.customProperties.deploymentPolicyLink)
        }
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled();
    }

    get isApplicationEmbedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    editHostCanceled() {
        this.clearView();
        this.onCancel.emit(null);
    }

    clearView() {
        this.resetAlert();

        this.isSavingHost = false;
        this.isVerifyingHost = false;
        this.isHostVerified = true;

        this.editHostForm.reset();
        this.editHostForm.markAsPristine();
        this.selectedCredential = null;
    }

    onCredentialsSelection(selectedCredential) {
        this.selectedCredential = selectedCredential;
    }

    getHostInputData() {
        var hostCopy = Object.assign({}, this.host);
        hostCopy.customProperties = Object.assign({}, this.host.customProperties);

        let formData = this.editHostForm.value;

        if (formData.name) {
            hostCopy.customProperties['__hostAlias'] = formData.name;
        }

        let authCredentialsLink =
            Utils.getCustomPropertyValue(this.host.customProperties, '__authCredentialsLink');
        this.isHostVerified = this.selectedCredential === authCredentialsLink;

        if (this.selectedCredential) {
            hostCopy.customProperties['__authCredentialsLink'] = this.selectedCredential;
        }

        // allow overwriting with empty value
        hostCopy.customProperties[Constants.hosts.customProperties.publicAddress] =
            formData.publicAddress || "";

        if (this.deploymentPolicySelection) {
            hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink] =
                this.deploymentPolicySelection;
        } else {
            delete hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink];
        }

        return hostCopy;
    }

    verifyHost() {
        if (this.editHostForm.valid) {
            this.isVerifyingHost = true;
            this.isHostVerified = false;

            let host = this.getHostInputData();
            let hostSpec = {
                'hostState': host
            };

            this.documentService.put(Links.CONTAINER_HOSTS + '?validate=true', hostSpec)
            .then((response) => {
                if (response === null) {
                    this.isVerifyingHost = false;
                    this.isHostVerified = true;

                    this.showAlertMessage(Constants.alert.type.SUCCESS, I18n.t('hosts.verified'));
                } else {
                    this.showCertificateWarning = true;
                    this.projectLinks = hostSpec.hostState.tenantLinks;
                    this.certificate = response;

                    let hostCustomProperties = hostSpec.hostState.customProperties;
                    this.trustCertLink = hostCustomProperties['__trustCertLink'];
                    this.hostUri = hostCustomProperties['__dockerUri'];
                }
            }).catch(error => {
                this.isVerifyingHost = false;

                this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
            });
        }
    }

    saveHost() {
        if (this.editHostForm.valid) {
            this.isSavingHost = true;

            var hostState = this.getHostInputData();
            let hostSpec = {
                'hostState': hostState,
                'isUpdateOperation': true
            };

            this.documentService.put(Links.CONTAINER_HOSTS, hostSpec)
            .then((response) => {
                this.clearView();

                this.onChange.emit(null);
            }).catch(error => {
                this.isSavingHost = false;

                this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
            });
        }
    }

    acceptCertificate() {
        this.showCertificateWarning = false;
        this.isVerifyingHost = false;
        this.isHostVerified = true;

        this.importOrUpdateCertificate();
    }

    cancelEditHost() {
        this.showCertificateWarning = false;
        this.isVerifyingHost = false;
    }

    /**
     * Reimports a host's certificate if for some reason it was removed from trusted
     * certificates while the host is still present.
     * Updates the trusted certificate of the host if for some reason the certificte
     * was changed on the host while the host is still present.
     */
    private importOrUpdateCertificate() {
        this.documentService.get(this.trustCertLink).then((response) => {
            this.documentService.patch(this.trustCertLink, {
                certificate: this.certificate
            }).then(() => {
                this.showAlertMessage(Constants.alert.type.SUCCESS, I18n.t('hosts.verified'));
            });
        }).catch((error) => {
            if (error.status === 404) {
                this.documentService.put(Links.SSL_TRUST_CERTS_IMPORT, {
                    hostUri: this.hostUri,
                    acceptCertificate: true,
                    tenantLinks: this.projectLinks
                }).then(() => {
                    this.showAlertMessage(Constants.alert.type.SUCCESS, I18n.t('hosts.verified'));
                });
            } else {
                this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
            }
        });
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
