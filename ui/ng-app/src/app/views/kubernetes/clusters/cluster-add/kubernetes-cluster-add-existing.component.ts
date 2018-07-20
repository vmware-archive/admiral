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

import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from "../../../../utils/project.service";
import { Constants } from "../../../../utils/constants";
import { Links } from "../../../../utils/links";
import { Utils } from "../../../../utils/utils";

import * as I18n from 'i18next';

const OPERATION_IN_PROGRESS = Constants.clusters.pks.lastActionState.inProgress;

@Component({
    selector: 'app-kubernetes-cluster-add-existing',
    templateUrl: './kubernetes-cluster-add-existing.component.html',
    styleUrls: ['./kubernetes-cluster-add-existing.component.scss']
})
/**
 * View for adding existing clusters.
 */
export class KubernetesClusterAddExistingComponent implements OnInit {
    loading: boolean = false;
    isAdding: boolean = false;

    endpoints: any[];
    selectedEndpoint: any;

    originalClusters: any[] = [];
    clusters: any[] = [];
    selectedClusters: any[] = [];

    // alert
    alertMessage: string;
    alertType: string;

    addExistingClustersForm = new FormGroup({
        endpoint: new FormControl('')
    });

    endpointsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    endpointsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(protected route: ActivatedRoute, protected router: Router,
                protected service: DocumentService, protected projectService: ProjectService,
                protected errorService: ErrorService) {
    }

    ngOnInit(): void {
        this.populateEndpoints();
    }

    populateEndpoints() {
        if (this.endpoints) {
            return;
        }

        this.service.list(Links.PKS_ENDPOINTS, {}, undefined, true)
        .then(result => {
            this.endpoints = result.documents;
        }).catch((error) => {
            console.error('PKS Endpoints listing failed', error);
            this.showErrorMessage(error);
        });
    }

    onChangeEndpoint(endpoint) {
        this.selectedEndpoint = endpoint;

        if (!this.selectedEndpoint) {
            this.clusters = [];
            this.selectedClusters = [];

            return;
        }

        this.loading = true;

        this.service.listWithParams(Links.PKS_CLUSTERS, { endpointLink: endpoint.documentSelfLink})
            .then((result) => {
                this.loading = false;

                // TODO clusters in provisioning (in process of adding to admiral)
                // state should not be selectable
                this.originalClusters = result.documents;

                this.clusters = this.originalClusters.map(resultDoc => {
                    return {
                        uuid: resultDoc.uuid,
                        name: resultDoc.name,
                        hostname: resultDoc.parameters.kubernetes_master_host,
                        plan: resultDoc.plan_name || '',
                        masterNodesCount: resultDoc.kubernetes_master_ips.length || 1,
                        workerNodesCount: resultDoc.parameters.kubernetes_worker_instances,
                        lastAction: resultDoc.last_action,
                        lastActionStatus: resultDoc.last_action_state,
                        addedInAdmiral: resultDoc.parameters.__clusterExists
                    };
                })
        }).catch(error => {
            this.loading = false;
            console.error('PKS Clusters listing failed', error);
            this.showErrorMessage(error);
        })
    }

    add() {
        if (this.selectedClusters.length !== 1) {
            // Currently only single cluster can be added
            this.showAlertMessage(Constants.alert.type.WARNING,
                I18n.t('pks.add.existingClusters.multipleSelectedClustersWarning'));
            return;
        }

        var selectedCluster = this.selectedClusters[0];

        if (selectedCluster.addedInAdmiral) {
            this.showAlertMessage(Constants.alert.type.WARNING,
                I18n.t('pks.add.existingClusters.clusterAlreadyRegisteredWarning'));
            return;
        }

        if (selectedCluster.lastActionStatus === OPERATION_IN_PROGRESS) {
            this.showAlertMessage(Constants.alert.type.WARNING,
                I18n.t('pks.add.existingClusters.operationInProgressWarning'));
            return;
        }

        this.resetAlert();

        this.isAdding = true;
        let clusterToAdd = this.originalClusters.find(originalCluster => {
                return originalCluster.uuid === selectedCluster.uuid;
        });

        let addClusterRequest = {
            'endpointLink': this.selectedEndpoint.documentSelfLink,
            'cluster': clusterToAdd
        };

        this.service.post(Links.PKS_CLUSTERS_ADD, addClusterRequest).then((result) => {
                this.isAdding = false;
                this.goBack();
        }).catch(error => {
            this.isAdding = false;
            console.error('Could not add PKS cluster', error);
            this.showErrorMessage(error);
        });
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }

    private showErrorMessage(error) {
        this.showAlertMessage(Constants.alert.type.DANGER, Utils.getErrorMessage(error)._generic);
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
