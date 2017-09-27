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

import { Component, Input, Output, EventEmitter } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from './../../../utils/document.service';
import { ProjectService } from './../../../utils/project.service';
import { Links } from './../../../utils/links';
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
export class ClusterAddHostComponent {

    @Input() cluster: any;
    @Input() visible: boolean;
    @Input() projectLink: string;
    @Input() credentials: any[] = [];

    isAddingHost: boolean;
    showCertificateWarning: boolean;
    certificate: any;
    alertMessage: string;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    addHostToClusterForm = new FormGroup({
        address: new FormControl('', Validators.required),
        credentials: new FormControl('')
    });

    constructor(private ds: DocumentService) { }

    clearView() {
        this.resetAlert();
        this.isAddingHost = false;
        this.addHostToClusterForm.reset();
        this.addHostToClusterForm.markAsPristine();
    }

    resetAlert() {
        this.alertMessage = null;
    }

    getCredentialsName(credentials) {
        let name = Utils.getCustomPropertyValue(credentials.customProperties, '__authCredentialsName');
        return name ? name : credentials.documentId;
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
                hostState.customProperties['__authCredentialsLink'] = formInput.credentials;
            }

            let hostSpec = {
                'hostState': hostState,
                'acceptCertificate': certificateAccepted
            };
            this.ds.post(this.cluster.documentSelfLink + '/hosts', hostSpec, this.projectLink).then((response) => {
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
