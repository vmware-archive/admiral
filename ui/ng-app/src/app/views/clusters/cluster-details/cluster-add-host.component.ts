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

import { Component, AfterViewInit, Input, Output, EventEmitter } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from './../../../utils/document.service';
import { Links } from './../../../utils/links';
import { Utils } from "../../../utils/utils";

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

    credentials: any[];
    alertMessage: string;
    isAddingHost: boolean;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    addHostToClusterForm = new FormGroup({
        address: new FormControl('', Validators.required),
        credentials: new FormControl('')
    });

    constructor(protected service: DocumentService) {}

    ngAfterViewInit() {
        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents;
        });
    }

    clearForm() {
        this.resetAlert();
        this.addHostToClusterForm.reset();
        this.addHostToClusterForm.markAsPristine();
    }

    resetAlert() {
        this.alertMessage = null;
    }

    addCanceled() {
        this.clearForm();
        this.onCancel.emit(null);
    }

    getCredentialsName(credentials) {
        let name = Utils.getCustomPropertyValue(credentials.customProperties, '__authCredentialsName');
        return name ? name : credentials.documentId;
    }

    addHost() {
        if (this.addHostToClusterForm.valid) {

        }
    }
}
