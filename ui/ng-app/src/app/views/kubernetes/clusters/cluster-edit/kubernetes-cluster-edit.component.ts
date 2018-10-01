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
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../../components/base/base-details.component';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from '../../../../utils/error.service';
import { ProjectService } from '../../../../utils/project.service';
import { Constants } from '../../../../utils/constants';
import { Links } from '../../../../utils/links';
import { Utils}  from '../../../../utils/utils';

@Component({
    selector: 'app-kubernetes-cluster-edit',
    templateUrl: './kubernetes-cluster-edit.component.html',
    styleUrls: ['./kubernetes-cluster-edit.component.scss']
})
/**
 * Edit a kubernetes cluster.
 */
export class KubernetesClusterEditComponent extends BaseDetailsComponent {
    endpointsLoading: boolean = false;
    endpoints: any[];
    preselectedEndpointLink: string;

    plansLoading: boolean = false;
    plans: any[];

    editClusterForm = new FormGroup({
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

    isUpdatingCluster: boolean = false;

    // alert
    alertMessage: string;
    alertType: string;

    selectedPlanId: any;

    constructor(protected route: ActivatedRoute, protected router: Router,
                protected documentService: DocumentService,
                protected projectService: ProjectService,
                protected errorService: ErrorService) {

        super(Links.CLUSTERS, route, router, documentService, projectService, errorService);

        this.populateEndpoints();
    }

    entityInitialized(): void {
        if (this.entity) {
            let clusterData = this.entity.nodeLinks && this.entity.nodeLinks.length > 0
                && this.entity.nodes && this.entity.nodes[this.entity.nodeLinks[0]];
            let clusterProperties = clusterData && clusterData.customProperties;

            // Endpoint
            this.preselectedEndpointLink =
                Utils.getCustomPropertyValue(clusterProperties, '__pksEndpoint');
            if (this.endpoints) {
                this.preselectEndpointOption();
            }
            // Name
            let clusterName = Utils.getCustomPropertyValue(clusterProperties, '__pksClusterName');
            this.editClusterForm.get('name').setValue(clusterName);
            this.editClusterForm.get('name').disable();
            // Plan
            this.selectedPlanId =
                Utils.getCustomPropertyValue(clusterProperties, 'plan_name');
            this.editClusterForm.get('plan').disable();

            this.documentService.listWithParams(Links.PKS_CLUSTERS,
                { endpointLink: this.preselectedEndpointLink, cluster: clusterName })
            .then((result) => {
                let clusters = result.documents;
                let theCluster = clusters.find((cluster) => {
                    return cluster.name === clusterName;
                });

                let params = theCluster && theCluster.parameters;
                if (params) {
                    // Master Host Name
                    this.editClusterForm.get('masterHostName')
                                                .setValue(params.kubernetes_master_host);
                    this.editClusterForm.get('masterHostName').disable();
                    // Master Host Port
                    this.editClusterForm.get('masterHostPort')
                                                .setValue(params.kubernetes_master_port);
                    this.editClusterForm.get('masterHostPort').disable();

                    // Number of workers - editable
                    this.editClusterForm.get('workerInstances')
                                                .setValue(params.kubernetes_worker_instances);
                }
            }).catch(error => {
                console.log('Failed to retrieve PKS cluster information', error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            });
        }
    }

    clearView() {
        this.editClusterForm.reset();
        this.editClusterForm.markAsPristine();
    }

    populateEndpoints() {
        this.endpointsLoading = true;

        this.documentService.list(Links.PKS_ENDPOINTS, {}).then(result => {
            this.endpointsLoading = false;

            this.endpoints = result.documents;
            this.preselectEndpointOption();
        }).catch((error) => {
            console.log(error);
            this.endpointsLoading = false;

            this.showErrorMessage(error);
        });
    }

    private preselectEndpointOption() {
        if (this.preselectedEndpointLink) {
            this.editClusterForm.get('endpoint').disable();
            this.populatePlans();
        }
    }

    private getAssignedPlans() {
        let endpoint = this.preselectedEndpointLink
                        && this.endpoints.find(e => e.documentSelfLink === this.preselectedEndpointLink);
        let assignedPlans;
        if (endpoint && endpoint.planAssignments) {
            let selectedProject = this.projectService.getSelectedProject();
            let projectLink = selectedProject
                                && (selectedProject.documentSelfLink || selectedProject.id);

            assignedPlans = endpoint.planAssignments[projectLink]
                                && endpoint.planAssignments[projectLink].plans;
        }

        return assignedPlans;
    }

    populatePlans() {
        let assignedPlans = this.getAssignedPlans();
        if (!assignedPlans) {
            this.plans = [];
            return;
        }

        this.plansLoading = true;
        this.documentService.listWithParams(Links.PKS_PLANS,{
            endpointLink: this.preselectedEndpointLink
            }).then((result) => {
            this.plansLoading = false;

            // show only plans for the currently selected group/project
            this.plans = result.documents.filter(resultDoc =>
                             assignedPlans.indexOf(resultDoc.name) !== -1);
        }).catch(error => {
            console.error('PKS Plans listing failed', error);
            this.plansLoading = false;

            this.showErrorMessage(error);
        });
    }

    update() {
        if (this.editClusterForm.valid) {
            this.isUpdatingCluster = true;

            let formValues = this.editClusterForm.value;

            let clusterUpdateSpec = {
                "resourceType": "PKS_CLUSTER",
                "operation": "RESIZE_RESOURCE",
                "resourceLinks": [this.entity.documentSelfLink],
                "customProperties": {
                    "kubernetes_worker_instances": formValues.workerInstances
                }
            };

            this.documentService.post(Links.REQUESTS, clusterUpdateSpec).then((response) => {
                this.isUpdatingCluster = false;
                this.goBack();
            }).catch(error => {
                this.isUpdatingCluster = false;

                console.error(error);
                this.showErrorMessage(error);
            });
        }
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../..'], {relativeTo: this.route});
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
