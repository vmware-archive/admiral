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

import { Component, ViewChild } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AutoRefreshComponent } from '../../../components/base/auto-refresh.component';
import { GridViewComponent } from '../../../components/grid-view/grid-view.component';
import { DocumentService } from '../../../utils/document.service';
import { ProjectService } from "../../../utils/project.service";
import { AuthService } from '../../../utils/auth.service';
import { ErrorService } from '../../../utils/error.service';
import { RoutesRestriction } from '../../../utils/routes-restriction';
import { Constants } from '../../../utils/constants';
import { FT } from '../../../utils/ft';
import { Utils } from '../../../utils/utils';
import { Links } from '../../../utils/links';


import * as I18n from 'i18next';

@Component({
    selector: 'app-kubernetes-clusters',
    templateUrl: './kubernetes-clusters.component.html',
    styleUrls: ['./kubernetes-clusters.component.scss']
})
/**
 * PKS/Kubernetes Clusters main grid view.
 */
export class KubernetesClustersComponent extends AutoRefreshComponent {
    @ViewChild('gridView') gridView: GridViewComponent;

    private userSecurityContext: any;

    projectLink: string;

    serviceEndpoint = Links.CLUSTERS + '?type=KUBERNETES';
    selectedItem: any;

    // delete/remove operations
    deleteOp: string;
    deleteOpCluster: any;
    deleteConfirmationError: string;

    constructor(protected service: DocumentService, protected projectService: ProjectService,
                protected router: Router, protected route: ActivatedRoute,
                protected authService: AuthService, protected errorService: ErrorService) {

        super(router, route, FT.allowHostEventsSubscription(),
                Utils.getClustersViewRefreshInterval(), true);

        Utils.subscribeForProjectChange(projectService, (changedProjectLink) => {
            this.projectLink = changedProjectLink;
        });

        authService.getCachedSecurityContext().then((securityContext) => {
            this.userSecurityContext = securityContext;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    ngOnInit(): void {
        this.refreshFnCallScope = this.gridView;
        this.refreshFn = this.gridView.autoRefresh;

        var me = this;
        this.gridView.processItemsFn = function(itemsValue) {
            let processedItems = itemsValue.map(itemVal => {
                if (me.operationSupported('ENABLE', itemVal)) {
                    itemVal.supportsOperationEnable = true;
                }
                if (me.operationSupported('DISABLE', itemVal)) {
                    itemVal.supportsOperationDisable = true;
                }
                if (me.operationSupported('DESTROY', itemVal)) {
                    itemVal.supportsOperationDestroy = true;
                }
                if (me.operationSupported('RESCAN', itemVal)) {
                    itemVal.supportsOperationRescan = true;
                }

                if (me.operationSupported('REMOVE', itemVal)) {
                    itemVal.supportsOperationRemove = true;
                }

                return itemVal;
            });

            return processedItems;
        };

        super.ngOnInit();
    }

    get deleteOpClusterName(): string {
        return this.deleteOpCluster && this.deleteOpCluster.name;
    }

    get deleteOpTitle() {
        let key = this.deleteOp === 'REMOVE'
                    ? 'kubernetes.clusters.remove.title'
                    : 'kubernetes.clusters.destroy.title';

        return this.deleteOpClusterName
            && I18n.t(key, {
                clusterName: this.deleteOpClusterName,
                interpolation: {escapeValue: false}
            } as I18n.TranslationOptions);
    }

    get deleteOpConfirmationDescription(): string {
        let description;
        if (!this.deleteOpClusterName) {
            return description;
        }

        let key = this.deleteOp === 'REMOVE'
                    ? 'kubernetes.clusters.remove.confirmation'
                    : 'kubernetes.clusters.destroy.confirmation';

        description = I18n.t(key, {
            clusterName: this.deleteOpClusterName,
            interpolation: { escapeValue: false }
        } as I18n.TranslationOptions);

        return description;
    }

    get deleteOpConfirmationBtnTextKey(): string {
        return (this.deleteOp === 'DESTROY') && 'destroy';
    }

    hasNodes(cluster) {
        return cluster && cluster.nodeLinks && cluster.nodeLinks.length > 0;
    }

    getClusterCustomProperties(cluster) {
        let properties;
        if (this.hasNodes(cluster)) {
            properties = cluster.nodes[cluster.nodeLinks[0]].customProperties;
        }

        return properties;
    }

    totalMemory(cluster) {
        if (cluster && cluster.totalMemory) {
            return this.formatNumber(cluster.totalMemory) + 'B';
        }
        return I18n.t('notAvailable');
    }

    downloadKubeConfig($event, cluster) {
        $event.stopPropagation();

        var hostLink = cluster.nodeLinks && cluster.nodeLinks[0];

        if (!hostLink) {
            console.log('cannot download kubeconfig: no hosts found');
            return;
        }

        var kubeConfigLink = Links.KUBE_CONFIG_CONTENT + '?hostLink=' + hostLink;
        window.location.href = Utils.serviceUrl(kubeConfigLink);
    }

    setDeleteOperation(deleteOperation, cluster) {
        this.deleteOp = deleteOperation;
        this.deleteOpCluster = cluster;
    }

    clearDeleteOpData() {
        this.deleteOp = undefined;
        this.deleteOpCluster = null;
    }

    removeCluster(event, cluster) {
        this.setDeleteOperation('REMOVE', cluster);
        event.stopPropagation();
        // clear selection
        this.selectedItem = null;
        return false; // prevents navigation
    }

    destroyCluster($event, cluster) {
        this.setDeleteOperation('DESTROY', cluster);

        event.stopPropagation();
        // clear selection
        this.selectedItem = null;

        return false; // prevents navigation
    }

    deleteOpConfirmed() {
        if (this.deleteOp === 'REMOVE') {
            // remove from admiral
            this.service.delete(this.deleteOpCluster.documentSelfLink, this.projectLink)
            .then(() => {
                this.clearDeleteOpData();

                this.gridView.refresh();
            }).catch(error => {
                this.deleteConfirmationError = Utils.getErrorMessage(error)._generic;
            });
        } else if (this.deleteOp === 'DESTROY') {
            // Delete cluster from pks
            let destroyClusterSpec = {
                "resourceType": "PKS_CLUSTER",
                "operation": "REMOVE_RESOURCE",
                "resourceLinks": [this.deleteOpCluster.documentSelfLink]
            };

            this.service.post(Links.REQUESTS, destroyClusterSpec).then((response) => {
                this.clearDeleteOpData();
                // refresh view to update status of the cluster.
                // Note: destroy is long running op
                this.gridView.refresh();
            }).catch(error => {
                this.deleteConfirmationError = Utils.getErrorMessage(error)._generic;
            });
        }
    }

    deleteOpCanceled() {
        this.clearDeleteOpData();
    }

    nodeCount(cluster) {
        if (cluster) {
            let nodesString = Utils.getCustomPropertyValue(
                                    this.getClusterCustomProperties(cluster), '__nodes');
            if (nodesString) {
                return JSON.parse(nodesString).length;
            }
        }

        return I18n.t('notAvailable');
    }

    clusterState(cluster) {
        return I18n.t('clusters.state.' + cluster.status);
    }

    formatNumber(number) {
        if (!number) {
            return '0';
        }
        let m = Utils.getMagnitude(number);
        return Utils.formatBytes(number, m) + ' ' + Utils.magnitudes[m];
    }

    getResourceLabel(b1, b2, unit) {
        if (b2 == 0) {
            return 'N/A';
        }

        let m = Utils.getMagnitude(b2);
        return Utils.formatBytes(b1, m) + ' of ' + Utils.formatBytes(b2, m)
                + Utils.magnitudes[m] + unit;
    }

    operationSupported(op, cluster) {
        let clusterStatus = cluster.status;

        let isClusterOwnedByCurrentUser = this.isClusterOwnedByCurrentUser(cluster);

        if (op === 'ENABLE') {
            // Enable
            return clusterStatus === Constants.clusters.status.DISABLED && isClusterOwnedByCurrentUser;
        } else if (op === 'DISABLE') {
            // Disable
            return clusterStatus === Constants.clusters.status.ON && isClusterOwnedByCurrentUser;
        } else if (op === 'DESTROY') {
            // Destroy
            return Utils.isPksCluster(cluster)
                    && clusterStatus !== Constants.clusters.status.PROVISIONING
                    && clusterStatus !== Constants.clusters.status.RESIZING
                    && clusterStatus !== Constants.clusters.status.DESTROYING
                    && clusterStatus !== Constants.clusters.status.UNREACHABLE
                    && isClusterOwnedByCurrentUser;
        } else if (op === 'REMOVE') {
            return isClusterOwnedByCurrentUser;
        }

        return true;
    }

    isClusterOwnedByCurrentUser(cluster) {
        let nodes = cluster.nodes;
        if (nodes && Utils.isContainerDeveloper(this.userSecurityContext)) {
            let user = this.userSecurityContext.user;
            for (var key in nodes) {
                let tenantLinks = nodes[key] && nodes[key].tenantLinks;
                if (tenantLinks.indexOf('/users/' + user) === -1) {
                    return false;
                }
            }
        }

        return true;
    }

    enableHost(event, cluster) {
        event.stopPropagation();

        if (!this.hasNodes(cluster)) {
            return;
        }

        var hostLink = cluster.nodeLinks[0];

        this.service.patch(hostLink, {'powerState': Constants.hosts.state.ON})
            .then(() => {

                this.gridView.refresh();
        }).catch((err) => {
            console.log(Utils.getErrorMessage(err)._generic);
        });

        return false; // prevents navigation
    }

    disableHost(event, cluster) {
        event.stopPropagation();

        if (!this.hasNodes(cluster)) {
            return;
        }

        var hostLink = cluster.nodeLinks[0];

        this.service.patch(hostLink, {'powerState': Constants.hosts.state.SUSPEND})
            .then(() => {

                this.gridView.refresh();
        }).catch((err) => {
            console.log(Utils.getErrorMessage(err)._generic);
        });

        return false; // prevents navigation
    }

    isItemSelected(item: any) {
        return item === this.selectedItem;
    }

    toggleItemSelection($event, item) {
        $event.stopPropagation();

        if (this.isItemSelected(item)) {
            // clear selection
            this.selectedItem = null;
        } else {
            this.selectedItem = item;
        }
    }

    get addClusterRouteRestriction() {
        return RoutesRestriction.KUBERNETES_CLUSTERS_ADD;
    }

    rescanCluster(event, cluster) {
        event.stopPropagation();
        // clear selection
        this.selectedItem = null;

        this.service.get(cluster.documentSelfLink + '/hosts')
        .then((clusterHostsResult) => {
            this.gridView.refresh();

            let computeContainerHostLinks = [];

            if (FT.isApplicationEmbedded()) {
                clusterHostsResult.content.forEach(element => {
                    computeContainerHostLinks.push(element.documentSelfLink);
                });
            } else {
                computeContainerHostLinks = clusterHostsResult.documentLinks;
            }

            let clusterHostsLinks = {
                computeContainerHostLinks: computeContainerHostLinks
            };
            // start hosts data collection
            this.service.patch(Links.HOST_DATA_COLLECTION, clusterHostsLinks, this.projectLink)
            .then((response) => {
            }).catch(error => {
                console.error('Rescan of cluster failed', Utils.getErrorMessage(error)._generic);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            });

        }).catch(error => {
            console.error('Cannot retrieve cluster resources',
                                                            Utils.getErrorMessage(error)._generic);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });

        return false; // prevents navigation
    }
}
