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

import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { AuthService } from '../../../../utils/auth.service';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from '../../../../utils/error.service';
import { Constants } from '../../../../utils/constants';
import { FT } from '../../../../utils/ft';
import { Links } from '../../../../utils/links';
import { ProjectService } from '../../../../utils/project.service';
import { RoutesRestriction } from '../../../../utils/routes-restriction';
import { Utils } from '../../../../utils/utils';

import * as I18n from 'i18next';

@Component({
    selector: 'app-kubernetes-cluster-summary',
    templateUrl: './kubernetes-cluster-summary.component.html',
    styleUrls: ['./kubernetes-cluster-summary.component.scss']
})

/**
 *  A kubernetes cluster's summary view.
 */
export class KubernetesClusterSummaryComponent implements OnInit {
    @Input() cluster: any;

    userSecurityContext: any;

    constructor(protected route: ActivatedRoute, protected router: Router,
                protected service: DocumentService, protected authService: AuthService,
                protected projectService: ProjectService, protected errorService: ErrorService) {

        if (!FT.isApplicationEmbedded()) {
            this.authService.getCachedSecurityContext().then((securityContext) => {
                this.userSecurityContext = securityContext;

            }).catch((ex) => {
                console.log(ex);
            });
        }
    }

    get clusterResourcesTextKey() {
        if (FT.isVic()) {
            return 'clusters.summary.clusterResourcesVic';
        }
        return 'clusters.summary.clusterResources';
    }

    get clusterState() {
        if (this.cluster) {
            return I18n.t('clusters.state.' + this.cluster.status);
        }
        return '';
    }

    get isPksCluster(): boolean {
        return Utils.isPksCluster(this.cluster);
    }

    get planName() {
        let plan;

        if (this.cluster) {
            plan = Utils.getCustomPropertyValue(this.clusterCustomProperties, '__pksPlanName');
        }

        return plan;
    }

    get masterNodesIPs() {
        if (this.cluster) {
            return Utils.getCustomPropertyValue(this.clusterCustomProperties, '__masterNodesIPs');
        }

        return '';
    }

    get hasNodes() {
        return this.cluster && this.cluster.nodeLinks && this.cluster.nodeLinks.length > 0;
    }

    get clusterFirstNodeLink() {
        return this.hasNodes && this.cluster.nodeLinks[0];
    }

    get clusterFirstNode() {
        return this.cluster && this.cluster.nodes && this.clusterFirstNodeLink
                    && this.cluster.nodes[this.clusterFirstNodeLink];
    }

    get clusterCustomProperties() {
        return this.clusterFirstNode && this.clusterFirstNode.customProperties;
    }

    get nodeCount() {
        let count;

        if (this.cluster) {
            let nodesString = Utils.getCustomPropertyValue(this.clusterCustomProperties, '__nodes');
            if (nodesString) {
                count = JSON.parse(nodesString).length;
            }
        }

        return count;
    }

    get totalMemory() {
        let total;

        if (this.cluster && this.cluster.totalMemory) {
            total = this.formatNumber(this.cluster.totalMemory) + 'B';
        }

        return total;
    }

    get dashboardLink() {
        return this.clusterCustomProperties
            && Utils.getCustomPropertyValue(this.clusterCustomProperties, '__dashboardLink');
    }

    get dashboardText() {
        let textDashboard;

        if (this.clusterCustomProperties) {
            let dashboardInstalled =
                Utils.getCustomPropertyValue(this.clusterCustomProperties, '__dashboardInstalled');

            if (dashboardInstalled === 'true') {
                textDashboard = I18n.t('kubernetes.clusters.details.summary.dashboardInstalled');
            }
            if (dashboardInstalled === 'false') {
                textDashboard = I18n.t('kubernetes.clusters.details.summary.dashboardNotInstalled');
            }
        }

        return textDashboard;
    }

    get isAllowedEditCluster() {
        let selectedProject = this.projectService.getSelectedProject();
        let projectSelfLink = selectedProject && selectedProject.documentSelfLink;

        return Utils.isAccessAllowed(this.userSecurityContext, projectSelfLink,
                                        RoutesRestriction.KUBERNETES_CLUSTERS_EDIT);
    }

    ngOnInit() {
        // DOM init
    }

    operationSupported(op) {
        if (this.cluster && op === 'EDIT') {
            return this.cluster.status === Constants.clusters.status.ON;
        }

        return true;
    }

    downloadKubeConfig() {
        if (!this.cluster) {
            return;
        }

        if (!this.clusterFirstNodeLink) {
            console.log('cannot download kubeconfig: no hosts found');
            return;
        }

        var kubeConfigLink = Links.KUBE_CONFIG_CONTENT + '?hostLink=' + this.clusterFirstNodeLink;
        window.location.href = Utils.serviceUrl(kubeConfigLink);
    }

    formatNumber(number) {
        if (!number) {
            return '0';
        }
        let m = Utils.getMagnitude(number);
        return Utils.formatBytes(number, m) + ' ' + Utils.magnitudes[m];
    }
}
