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
import { Utils } from '../../../../utils/utils';
import { FT } from '../../../../utils/ft';
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

    get isPKSCluster(): boolean {
        if (this.cluster) {
            return Utils.getCustomPropertyValue(this.clusterCustomProperties, '__pksClusterUUID');
        }
        return false;
    }

    get planName() {
        if (this.cluster) {
            return Utils.getCustomPropertyValue(this.clusterCustomProperties, '__pksPlanName');
        }
        return I18n.t('notAvailable');
    }

    get clusterCustomProperties() {
        let properties;
        if (this.cluster && this.cluster.nodes && this.cluster.nodeLinks
                && this.cluster.nodeLinks.length > 0) {
            properties = this.cluster.nodes[this.cluster.nodeLinks[0]].customProperties;
        }

        return properties;
    }

    get nodeCount() {
        if (this.cluster) {
            var nodesString = Utils.getCustomPropertyValue(this.clusterCustomProperties, '__nodes');
            if (nodesString) {
                return JSON.parse(nodesString).length;
            }
        }

        return I18n.t('notAvailable');
    }

    get dashboard() {
        if (this.clusterCustomProperties) {

            var dashboardLink =
                Utils.getCustomPropertyValue(this.clusterCustomProperties, '__dashboardLink');
            if (dashboardLink) {
                var labelInstalled = I18n.t('kubernetes.clusters.details.summary.dashboardInstalled');
                return `<a href="${dashboardLink}" target="_blank">${labelInstalled}</a>`;
            }
            var dashboardInstalled =
                Utils.getCustomPropertyValue(this.clusterCustomProperties, '__dashboardInstalled');
            if (dashboardInstalled === 'true') {
                return I18n.t('kubernetes.clusters.details.summary.dashboardInstalled');
            }
            if (dashboardInstalled === 'false') {
                return I18n.t('kubernetes.clusters.details.summary.dashboardNotInstalled');
            }
        }
        return I18n.t('notAvailable');
    }

    ngOnInit() {
        // DOM init
    }

    formatNumber(number) {
        if (!number) {
            return '0';
        }
        let m = Utils.getMagnitude(number);
        return Utils.formatBytes(number, m) + ' ' + Utils.magnitudes[m];
    }
}
