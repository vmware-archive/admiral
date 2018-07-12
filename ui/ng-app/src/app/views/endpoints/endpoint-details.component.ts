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
import { BaseDetailsComponent } from '../../components/base/base-details.component';
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Constants } from "../../utils/constants";
import { Links } from "../../utils/links";
import { Utils } from "../../utils/utils";

@Component({
    selector: 'app-endpoint-details',
    templateUrl: './endpoint-details.component.html',
    styleUrls: ['./endpoint-details.component.scss']
})
/**
 * Endpoint details view.
 */
export class EndpointDetailsComponent extends BaseDetailsComponent {
    editMode: boolean = false;

    loadingClusters: boolean = false;
    clusters: any[] = [];

    alertType: any;
    alertMessage: string;

    constructor(route: ActivatedRoute, documentService: DocumentService, router: Router,
                errorService: ErrorService) {
        super(Links.PKS_ENDPOINTS, route, router, documentService, errorService);
    }

    protected entityInitialized() {
        if (this.entity) {
            this.editMode = true;
            // Load clusters for the endpoint
            this.loadingClusters = true;

            this.service.listWithParams(Links.PKS_CLUSTERS, { endpointLink: this.entity.documentSelfLink})
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
                        hostname: resultDoc.parameters.kubernetes_master_host,
                        plan: planName || '',
                        masterNodesCount: masterNodesCount || 1,
                        workerNodesCount: resultDoc.parameters.kubernetes_worker_instances,
                        lastAction: resultDoc.last_action,
                        lastActionStatus: resultDoc.last_action_state,
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

    private showErrorMessage(error) {
        this.alertType = Constants.alert.type.DANGER;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
