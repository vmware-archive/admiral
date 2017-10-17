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

import { Component, AfterViewInit, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from './../../../utils/document.service';
import { ProjectService } from './../../../utils/project.service';
import { Links } from './../../../utils/links';
import { Utils } from "../../../utils/utils";
import { FT } from './../../../utils/ft';
import { Constants } from '../../../utils/constants';
import * as I18n from 'i18next';

@Component({
    selector: 'app-cluster-edit-host',
    templateUrl: './cluster-edit-host.component.html',
    styleUrls: ['./cluster-edit-host.component.scss']
})
/**
 * Edit Cluster Hosts dialog.
 */
export class ClusterEditHostComponent implements OnChanges {

    @Input() visible: boolean;
    @Input() host: any;
    @Input() credentials: any[] = [];
    @Input() deploymentPolicies: any[] = [];

    isSavingHost: boolean;
    isVerifyingHost: boolean;
    isHostVerified: boolean = true;

    alertMessage: string;
    alertType: string;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    editHostForm = new FormGroup({
        name: new FormControl(''),
        credentials: new FormControl(''),
        publicAddress: new FormControl(''),
        deploymentPolicy: new FormControl('')
    });

    deploymentPoliciesTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.deploymentPolicy.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    deploymentPoliciesSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.deploymentPolicy.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(private ds: DocumentService) { }

    ngOnChanges(changes: SimpleChanges) {
        if (this.host) {

            this.editHostForm.get('name').setValue(Utils.getHostName(this.host));

            var authCredentialsLink = Utils.getCustomPropertyValue(this.host.customProperties, '__authCredentialsLink');
            if (authCredentialsLink) {
                this.editHostForm.get('credentials').setValue(authCredentialsLink);
            }

            var publicAddress = Utils.getCustomPropertyValue(this.host.customProperties,
                Constants.hosts.customProperties.publicAddress) || "";
            this.editHostForm.get('publicAddress').setValue(publicAddress);

            var deploymentPolicyLink = Utils.getCustomPropertyValue(this.host.customProperties,
                    Constants.hosts.customProperties.deploymentPolicyLink);
            this.editHostForm.get('deploymentPolicy').setValue(deploymentPolicyLink);
        }
    }

    get showPublicAddressField(): boolean {
        return FT.isHostPublicUriEnabled();
      }

    getCredentialsName(credentials) {
        let name = Utils.getCustomPropertyValue(credentials.customProperties, '__authCredentialsName');
        return name ? name : credentials.documentId;
    }

    editHostCanceled() {
        this.clearView();
        this.onCancel.emit(null);
    }

    resetAlert() {
        this.alertMessage = null;
    }

    clearView() {
        this.resetAlert();
        this.isSavingHost = false;
        this.isVerifyingHost = false;
        this.editHostForm.reset();
        this.editHostForm.markAsPristine();
    }

    onCredentialsChange() {
        var authCredentialsLink = Utils.getCustomPropertyValue(this.host.customProperties, '__authCredentialsLink');
        this.isHostVerified = this.editHostForm.value.credentials === authCredentialsLink;
    }

    getInputHost() {
        var hostCopy = Object.assign({}, this.host);
        hostCopy.customProperties = Object.assign({}, this.host.customProperties);
        let formInput = this.editHostForm.value;

        if (formInput.name) {
            hostCopy.customProperties['__hostAlias'] = formInput.name;
        }

        if (formInput.credentials) {
            hostCopy.customProperties['__authCredentialsLink'] = formInput.credentials;
        }

        // allow ovewriting with empty value
        hostCopy.customProperties[Constants.hosts.customProperties.publicAddress] = formInput.publicAddress || "";

        if (formInput.deploymentPolicy) {
            hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink] =
                    formInput.deploymentPolicy.documentSelfLink;
        } else {
            delete hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink];
        }

        return hostCopy;
    }

    verifyHost(){
        if (this.editHostForm.valid) {
            this.isVerifyingHost = true;

            var host = this.getInputHost();

            let hostSpec = {
                'hostState': host
            };

            this.ds.put(Links.CONTAINER_HOSTS + '?validate=true', hostSpec).then((response) => {
                this.isVerifyingHost = false;
                this.isHostVerified = true;
                this.alertType = Constants.alert.type.SUCCESS;
                this.alertMessage = I18n.t('hosts.verified');
            }).catch(error => {
                this.isVerifyingHost = false;
                this.alertType = Constants.alert.type.DANGER;
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }

    saveHost() {
        if (this.editHostForm.valid) {
            this.isSavingHost = true;

            var host = this.getInputHost();

            let hostSpec = {
                'hostState': host,
                'isUpdateOperation': true
            };

            this.ds.put(Links.CONTAINER_HOSTS, hostSpec).then((response) => {
                this.clearView();
                this.onChange.emit(null);
            }).catch(error => {
                this.isSavingHost = false;
                this.alertType = Constants.alert.type.DANGER;
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }
}
