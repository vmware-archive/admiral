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
import { Links } from "../../../../utils/links";
import { Utils } from "../../../../utils/utils";

import * as I18n from 'i18next';

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

    clusters: any[] = [];
    selectedClusters: any[] = [];

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

        this.service.list(Links.PKS_ENDPOINTS, {}).then(result => {
            this.endpoints = result.documents;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
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

        this.service.listPksClusters({ endpointLink: endpoint.documentSelfLink})
            .then((result) => {
                this.loading = false;

                // TODO clusters in provisioning (in process of adding to admiral)
                // state should not be selectable
                this.clusters = result.documents.map(resultDoc => {
                    return {
                        name: resultDoc.name,
                        plan: resultDoc.plan_name,
                        masterNodesCount: resultDoc.kubernetes_master_ips.length,
                        workerNodesCount: resultDoc.parameters.kubernetes_worker_instances,
                        addedInAdmiral: resultDoc.parameters.__clusterExists
                    };
                })
        }).catch(error => {
            this.loading = false;

            console.log(error);
        })
    }

    add() {
        let suitableForAddClusters = this.getSelectedClustersSuitableForAdd();
        if (suitableForAddClusters.length === 0) {
            // no suitable clusters for adding
            return;
        }

        // TODO Add the suitable clusters to the business group
        let selectedProject = this.projectService.getSelectedProject().documentSelfLink;
        console.log('adding to business group', selectedProject);

        this.goBack();
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }

    getSelectedClustersSuitableForAdd() {
        return this.selectedClusters.filter(cluster => !cluster.addedInAdmiral);
    }
}
