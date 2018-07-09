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

import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from "../../../../utils/project.service";
import { Constants } from "../../../../utils/constants";
import { Links } from "../../../../utils/links";
import { Utils}  from "../../../../utils/utils";

import * as I18n from 'i18next';

@Component({
    selector: 'app-kubernetes-cluster-new-settings',
    templateUrl: './kubernetes-cluster-new-settings.component.html'
})
/**
 * New kubernetes cluster view - settings tab.
 */
export class KubernetesClusterNewSettingsComponent implements OnInit {
    endpoints: any[];

    isCreatingCluster: boolean = false;

    // alert
    alertMessage: string;
    alertType: string;

    newClusterSettingsForm = new FormGroup({
        endpoint: new FormControl(''),
        name: new FormControl('', Validators.required),
        plan: new FormControl(''),
        masterHostName: new FormControl(''),
        masterHostPort: new FormControl('', Validators.pattern('[\\d]+')),
        workerInstances: new FormControl(1, Validators.compose(
                                        [ Validators.min(1),
                                            Validators.pattern('[\\d]+'), Validators.required ]))
    });

    planSelection: any = 'SMALL';

    endpointsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    endpointsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(protected route: ActivatedRoute, protected router: Router,
                protected documentService: DocumentService,
                protected projectService: ProjectService, protected errorService: ErrorService) {
        //
    }

    ngOnInit(): void {
        this.populateEndpoints();
    }

    populateEndpoints() {
        if (this.endpoints) {
            return;
        }

        this.documentService.list(Links.PKS_ENDPOINTS, {}).then(result => {
            this.endpoints = result.documents;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    endpointSelected(endpoint) {
        // TODO Retrieve available plans
    }

    create() {
        if (this.newClusterSettingsForm.valid) {
            this.isCreatingCluster = true;

            let formValues = this.newClusterSettingsForm.value;

            let clusterSpec = {
                "resourceType": "PKS_CLUSTER",
                "operation": "PROVISION_RESOURCE",
                "customProperties": {
                    "__pksEndpoint": formValues.endpoint.documentSelfLink,
                    "__pksClusterName": formValues.name,
                    "plan_name": formValues.plan,
                    "kubernetes_master_host": formValues.masterHostName,
                    "kubernetes_master_port": formValues.masterHostPort,
                    "kubernetes_worker_instances": formValues.workerInstances
                }
            };

            this.documentService.post(Links.REQUESTS, clusterSpec,
                                        this.projectService.getSelectedProject().documentSelfLink)
                .then((response) => {

                    this.isCreatingCluster = false;
                    this.goBack();
            }).catch(error => {
                this.isCreatingCluster = false;

                console.error(error);
                this.showErrorMessage(error);
            });
        }
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }


    private showErrorMessage(error) {
        this.alertType = Constants.alert.type.DANGER;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
