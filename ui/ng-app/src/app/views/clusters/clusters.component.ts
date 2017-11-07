/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { RoutesRestriction } from './../../utils/routes-restriction';
import { Component, ViewChild, OnInit, Input } from '@angular/core';
import { Links } from '../../utils/links';
import { DocumentService } from '../../utils/document.service';
import { ProjectService } from "../../utils/project.service";
import * as I18n from 'i18next';
import { Utils } from '../../utils/utils';
import { FT } from '../../utils/ft';
import { GridViewComponent } from '../../components/grid-view/grid-view.component';

@Component({
  selector: 'app-clusters',
  templateUrl: './clusters.component.html',
  styleUrls: ['./clusters.component.scss']
})
export class ClustersComponent implements OnInit {
  @Input() hideTitle: boolean = false;
  @Input() projectLink: string;

  serviceEndpoint = Links.CLUSTERS;
  clusterToDelete: any;
  deleteConfirmationAlert: string;

  selectedItem: any;

  @ViewChild('gridView') gridView:GridViewComponent;

  constructor(private service: DocumentService, private projectService: ProjectService) {

    projectService.activeProject.subscribe((value) => {
      if (value && value.documentSelfLink) {
          this.projectLink = value.documentSelfLink;
      } else if (value && value.id) {
          this.projectLink = value.id;
      } else {
          this.projectLink = undefined;
      }
    });
  }

  ngOnInit() {
  }

  get deleteConfirmationDescription(): string {
    if (FT.isVic()) {
      return this.clusterToDelete && this.clusterToDelete.name
        && I18n.t('clusters.delete.confirmationVic', { clusterName:  this.clusterToDelete.name,
          interpolation: { escapeValue: false } } as I18n.TranslationOptions);
    }
    return this.clusterToDelete && this.clusterToDelete.name
      && I18n.t('clusters.delete.confirmation', { clusterName:  this.clusterToDelete.name,
        interpolation: { escapeValue: false } } as I18n.TranslationOptions);
  }

  get deleteTitle() {
    if (FT.isVic()) {
      return this.clusterToDelete && this.clusterToDelete.name
        && I18n.t('clusters.delete.titleVic');
    }
    return this.clusterToDelete && this.clusterToDelete.name
      && I18n.t('clusters.delete.title');
  }

  get title() {
    if (FT.isVic()) {
      return I18n.t('clusters.titleVic');
    }
    return I18n.t('clusters.title');
  }

  deleteCluster(event, cluster) {
    this.clusterToDelete = cluster;
    event.stopPropagation();
    // clear selection
    this.selectedItem = null;

    return false; // prevents navigation
  }

  deleteConfirmed() {
    this.service.delete(this.clusterToDelete.documentSelfLink, this.projectLink)
        .then(result => {
          this.clusterToDelete = null;
          this.gridView.refresh();
        })
        .catch(err => {
          this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
  }

  deleteCanceled() {
    this.clusterToDelete = null;
  }

  get isSupportedRescan() {
    return FT.allowHostEventsSubscription();
  }

  rescanCluster(event, cluster) {
    event.stopPropagation();
    // clear selection
    this.selectedItem = null;

    this.service.get(cluster.documentSelfLink + '/hosts')
                    .then((result) => {
      let clusterStatesToUpdate = {
        computeContainerHostLinks: result.documentLinks
      };

      this.service.patch(Links.HOST_DATA_COLLECTION, clusterStatesToUpdate, this.projectLink)
        .then((response) => {

        this.refreshCluster(cluster, Utils.getClusterRescanRetriesNumber(), null);

      }).catch(error => {
        console.error('Rescan of cluster failed', Utils.getErrorMessage(error)._generic);
      });

    }).catch(error => {
      console.error('Cannot retrieve cluster resources', Utils.getErrorMessage(error)._generic);
   });

    return false; // prevents navigation
  }

  refreshCluster(cluster, retries, timeoutId) {

    this.service.get(cluster.documentSelfLink).then((updatedCluster) => {
      // update cluster information
      cluster.status = updatedCluster.status;
      cluster.containerCount = updatedCluster.containerCount;
      cluster.totalMemory = updatedCluster.totalMemory;
      cluster.memoryUsage = updatedCluster.memoryUsage;
      cluster.nodeLinks = updatedCluster.nodeLinks;
      cluster.nodes = updatedCluster.nodes;
      cluster.totalCpu = updatedCluster.totalCpu;
      cluster.cpuUsage = updatedCluster.cpuUsage;
      // TODO more fields?
    });

    if (retries > 1) {
      clearTimeout(timeoutId);

      var __this = this;
      let timeoutIdNew = setTimeout(function() {
          __this.refreshCluster(cluster, retries - 1, timeoutIdNew);
      }, Utils.getClusterRescanInterval());
    }
  }

  cpuPercentageLevel(cluster) {
    if (!cluster){
      return 0;
    }
    return Math.floor(cluster.cpuUsage / cluster.totalCpu * 100);
  }

  memoryPercentageLevel(cluster) {
    if (!cluster){
      return 0;
    }
    return Math.floor(cluster.memoryUsage / cluster.totalMemory * 100);
  }

  getResourceLabel(b1, b2, unit) {
    if (b2 == 0) {
      return 'N/A';
    }
    let m = Utils.getMagnitude(b2);
    return Utils.formatBytes(b1, m) + ' of ' + Utils.formatBytes(b2, m) + Utils.magnitudes[m] + unit;
  }

  clusterState(cluster) {
    return I18n.t('clusters.state.' + cluster.status);
  }

  selectItem($event, item) {
    $event.stopPropagation();

    if (this.isItemSelected(item)) {
      // clear selection
      this.selectedItem = null;
    } else {
      this.selectedItem = item;
    }
  }

  isItemSelected(item: any) {
    return item === this.selectedItem;
  }

  get clustersNewRouteRestrictions() {
    return RoutesRestriction.CLUSTERS_NEW;
  }

  get clustersCardViewActions() {
    return RoutesRestriction.CLUSTERS_ID;
  }

 }
