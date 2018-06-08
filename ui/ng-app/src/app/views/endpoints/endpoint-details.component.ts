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

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { BaseDetailsComponent } from '../../components/base/base-details.component';
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Constants } from "../../utils/constants";
import { Links } from "../../utils/links";
import { Utils } from "../../utils/utils";

import * as I18n from 'i18next';


@Component({
    selector: 'app-endpoint-details',
    templateUrl: './endpoint-details.component.html',
    styleUrls: ['./endpoint-details.component.scss']
})
/**
 * View for endpoint creation.
 */
export class EndpointDetailsComponent extends BaseDetailsComponent {
    editMode: boolean = false;

    credentials: any[];

    endpointDetailsForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl(''),
        uaaAddress: new FormControl('', Validators.required),
        uaaCredentials: new FormControl(''),
        pksAddress: new FormControl('', Validators.required)
    });

    isSavingEndpoint: boolean = false;
    isTestingConnection: boolean = false;

    loadingClusters: boolean = false;
    clusters: any[] = [];

    alertType: any;
    alertMessage: string;

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(route: ActivatedRoute, documentService: DocumentService, router: Router,
                errorService: ErrorService) {
        super(Links.PKS_ENDPOINTS, route, router, documentService, errorService);
    }

    protected entityInitialized() {
        if (this.entity) {
            this.editMode = true;
            this.endpointDetailsForm.get('name').setValue(this.entity.name);
            this.endpointDetailsForm.get('description').setValue(this.entity.desc);
            this.endpointDetailsForm.get("uaaAddress").setValue(this.entity.uaaEndpoint);
            this.endpointDetailsForm.get("pksAddress").setValue(this.entity.apiEndpoint);

            // Load clusters for the endpoint
            this.loadingClusters = true;
            this.service.listPksClusters({ endpointLink: this.entity.documentSelfLink})
                .then((result) => {
                this.loadingClusters = false;

                // TODO clusters in provisioning (in process of adding to admiral)
                // state should not be selectable
                this.clusters = result.documents.map(resultDoc => {
                    // cafe uses different data format
                    let masterNodesCount = resultDoc.kubernetes_master_ips
                        ? resultDoc.kubernetes_master_ips.length
                        : resultDoc.masterIPs && resultDoc.masterIPs.length;
                    let planName = resultDoc.plan_name ? resultDoc.plan_name : resultDoc.planName;

                    return {
                        name: resultDoc.name,
                        plan: planName || '',
                        masterNodesCount: masterNodesCount || 1,
                        workerNodesCount: resultDoc.parameters.kubernetes_worker_instances,
                        addedInAdmiral: resultDoc.parameters.__clusterExists
                    };
                })
            }).catch(error => {
                this.loadingClusters = false;
                this.clusters = [];

                console.error('PKS Clusters listing for endpoint failed', error);
                this.showErrorMessage(error);
            })
        }
    }

    ngOnInit() {
        super.ngOnInit();

        this.populateCredentials();
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                                    .filter(c => !Utils.areSystemScopedCredentials(c))
                                    .map(Utils.toCredentialViewModel);

            if (this.entity && this.entity.authCredentialsLink) {
                let credItem = this.credentials && this.credentials.filter((c) =>
                    c.documentSelfLink === this.entity.authCredentialsLink
                );
                if (credItem && credItem.length > 0) {
                    this.endpointDetailsForm.get('uaaCredentials').setValue(credItem[0]);
                }
            }
        }).catch((error) => {
            console.error('Credentials retrieval failed', error);
            this.showErrorMessage(error);
        });
    }

    create() {
        if (this.endpointDetailsForm.valid) {
            this.isSavingEndpoint = true;

            this.service.post(Links.PKS_ENDPOINTS, this.getEndpointData()).then(() => {
                this.isSavingEndpoint = false;

                this.goBack();
            }).catch(error => {
                this.isSavingEndpoint = false;
                console.error('Failed to create endpoint', error);
                this.showErrorMessage(error);
            });
        }
    }

    save() {
        if (this.endpointDetailsForm.valid) {
            this.isSavingEndpoint = true;

            this.service.patch(this.entity.documentSelfLink, this.getEndpointData())
                            .then(() => {
                this.isSavingEndpoint = false;

                this.goBack();
            }).catch(error => {
                this.isSavingEndpoint = false;
                if (error.status === 304) {
                    // it's ok
                    this.goBack();
                }

                console.error('Failed to save endpoint', error);
                this.showErrorMessage(error);
            });
        }
    }

    testConnection() {
        if (this.endpointDetailsForm.valid) {
            // TODO - if success show green message - connection success, else show red err message
            // this.isTestingConnection = true;
            //
            // this.alertType = Constants.alert.type.SUCCESS;
            // this.alertMessage = I18n.t('endpoint.connectionVerified');
        }
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['..'], {relativeTo: this.route});
    }

    private showErrorMessage(error) {
        this.alertType = Constants.alert.type.DANGER;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }

    getEndpointData() {
        let endpointData;

        if (this.endpointDetailsForm.valid) {
            this.isSavingEndpoint = true;

            let uaaCredentialsLink = this.endpointDetailsForm.get("uaaCredentials").value
                && this.endpointDetailsForm.get("uaaCredentials").value.documentSelfLink;

            endpointData = {
                name: this.endpointDetailsForm.get("name").value,
                desc: this.endpointDetailsForm.get("description").value,
                uaaEndpoint: this.endpointDetailsForm.get("uaaAddress").value,
                apiEndpoint: this.endpointDetailsForm.get("pksAddress").value,
                authCredentialsLink: uaaCredentialsLink
            };
        }

        return endpointData;
    }
}
