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

import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
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
export class KubernetesClusterNewSettingsComponent implements OnInit, OnChanges {
    @Input() cluster: any;

    projectLink: string;

    editMode: boolean = false;
    endpoints: any[];
    endpointDocumentSelfLink: string;

    isCreatingCluster: boolean = false;

    // alert
    alertMessage: string;
    alertType: string;

    newClusterSettingsForm = new FormGroup({
        endpoint: new FormControl(''),
        name: new FormControl('', Validators.required),
        plan: new FormControl('', Validators.required),
        masterHostName: new FormControl('', Validators.required),
        masterHostPort: new FormControl('',
            Validators.compose([ Validators.pattern('[\\d]+'), Validators.required ])),
        workerInstances: new FormControl(1,
            Validators.compose([ Validators.min(1),
                Validators.pattern('[\\d]+'), Validators.required ]))
    });

    plansLoading: boolean = false;
    plans: any[];
    selectedPlan: any;
    // selected plan id
    private _selectedPlanId: string;

    set planSelection(value: string) {
        this._selectedPlanId = value;
        let formFieldWorkers = this.newClusterSettingsForm.get('workerInstances');
        if (this._selectedPlanId) {
            this.selectedPlan = this.plans.find(plan => plan.id === this.planSelection);
            formFieldWorkers.setValue(this.selectedPlan.worker_instances);
        } else {
            this.selectedPlan = undefined;
            formFieldWorkers.setValue(1);
        }
    }

    get planSelection(): string {
        if (!this._selectedPlanId && this.selectedPlan) {
            this._selectedPlanId = this.selectedPlan.id;
        }

        return this._selectedPlanId;
    }

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
                protected projectService: ProjectService,
                protected errorService: ErrorService) {

        projectService.activeProject.subscribe((value) => {
            if (value && value.documentSelfLink) {
                this.projectLink = value.documentSelfLink;
            } else if (value && value.id) {
                this.projectLink = value.id;
            } else {
                this.projectLink = undefined;
            }

            this.clearView();
            this.populateEndpoints();
        });
    }

    ngOnInit(): void {
        this.populateEndpoints();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (this.cluster) {
            this.editMode = true;

            let clusterData = this.cluster.nodeLinks && this.cluster.nodeLinks.length > 0
                && this.cluster.nodes && this.cluster.nodes[this.cluster.nodeLinks[0]];
            let clusterProperties = clusterData && clusterData.customProperties;

            // Endpoint
            this.endpointDocumentSelfLink =
                Utils.getCustomPropertyValue(clusterProperties, '__pksEndpoint');
            if (this.endpoints) {
                this.preselectEndpointOption();
            }
            // Name
            let clusterName = Utils.getCustomPropertyValue(clusterProperties, '__pksClusterName');
            this.newClusterSettingsForm.get('name').setValue(clusterName);
            this.newClusterSettingsForm.get('name').disable(true);
            // Plan
            this.newClusterSettingsForm.get('plan').setValue(
                Utils.getCustomPropertyValue(clusterProperties, 'plan_name'));
            this.newClusterSettingsForm.get('plan').disable(true);

            this.documentService.listWithParams(Links.PKS_CLUSTERS,
                    { endpointLink: this.endpointDocumentSelfLink, cluster: clusterName })
                .then((result) => {
                    let clusters = result.documents;
                    let theCluster = clusters.find((cluster) => {
                        return cluster.name === clusterName;
                    });

                    let params = theCluster && theCluster.parameters;
                    if (params) {
                        // Master Host Name
                        this.newClusterSettingsForm.get('masterHostName')
                                                        .setValue(params.kubernetes_master_host);
                        this.newClusterSettingsForm.get('masterHostName').disable(true);
                        // Master Host Port
                        this.newClusterSettingsForm.get('masterHostPort')
                                                        .setValue(params.kubernetes_master_port);
                        this.newClusterSettingsForm.get('masterHostPort').disable(true);

                        // Number of workers - editable
                        this.newClusterSettingsForm.get('workerInstances')
                                                    .setValue(params.kubernetes_worker_instances);
                    }
            }).catch(error => {
                console.log('Failed to retrieve PKS cluster information', error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            });
        }
    }

    clearView() {
        this.newClusterSettingsForm.reset();
        this.newClusterSettingsForm.markAsPristine();
    }

    populateEndpoints() {
        this.documentService.list(Links.PKS_ENDPOINTS, {}, this.projectLink)
        .then(result => {
            this.endpoints = result.documents;
            this.preselectEndpointOption();
        }).catch((error) => {
            console.log(error);
            this.showErrorMessage(error);
        });
    }

    private preselectEndpointOption() {
        if (this.endpointDocumentSelfLink) {
            let endpointOption = this.endpoints.find(endpoint =>
                endpoint.documentSelfLink === this.endpointDocumentSelfLink);
            this.newClusterSettingsForm.get('endpoint').setValue(endpointOption);
            this.newClusterSettingsForm.get('endpoint').disable(true);
        }
    }

    endpointSelected(endpoint) {
        if (endpoint && endpoint.planAssignments) {
            let assignedPlans = endpoint.planAssignments[this.projectLink].plans;

            this.plansLoading = true;

            this.documentService.listWithParams(Links.PKS_PLANS,
                            {endpointLink: endpoint.documentSelfLink || endpoint})
            .then((result) => {
                this.plansLoading = false;
                // show only plans for the currently selected group/project
                this.plans = result.documents.filter(resultDoc =>
                                                    assignedPlans.indexOf(resultDoc.name) !== -1);
                if (!this.editMode) {
                    this.planSelection = (this.plans.length > 0 && this.plans[0]).id;
                }
            }).catch(error => {
                console.error('PKS Plans listing failed', error);
                this.plansLoading = false;
                this.showErrorMessage(error);
            });
        } else {
            this.planSelection = undefined;
            this.plans = [];
        }
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
                    "plan_name": this.selectedPlan.name,
                    "kubernetes_master_host": formValues.masterHostName,
                    "kubernetes_master_port": formValues.masterHostPort,
                    "kubernetes_worker_instances": formValues.workerInstances
                }
            };

            this.documentService.post(Links.REQUESTS, clusterSpec).then((response) => {
                this.isCreatingCluster = false;
                this.goBack();
            }).catch(error => {
                this.isCreatingCluster = false;

                console.error(error);
                this.showErrorMessage(error);
            });
        }
    }

    update() {
        if (this.newClusterSettingsForm.valid) {
            this.isCreatingCluster = true;

            let formValues = this.newClusterSettingsForm.value;

            let clusterUpdateSpec = {
                "resourceType": "PKS_CLUSTER",
                "operation": "RESIZE_RESOURCE",
                "resourceLinks": [this.cluster.documentSelfLink],
                "customProperties": {
                    "kubernetes_worker_instances": formValues.workerInstances
                }
            };

            this.documentService.post(Links.REQUESTS, clusterUpdateSpec).then((response) => {

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
        let pathBack = this.editMode ? '../' : '../clusters';
        this.router.navigate([pathBack], {relativeTo: this.route});
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
