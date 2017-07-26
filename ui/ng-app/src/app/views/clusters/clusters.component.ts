import { RoutesRestriction } from './../../utils/routes-restriction';
import { Component, ViewChild, OnInit, Input } from '@angular/core';
import { Links } from '../../utils/links';
import { DocumentService } from '../../utils/document.service';
import * as I18n from 'i18next';
import { Utils } from '../../utils/utils';
import { GridViewComponent } from '../../components/grid-view/grid-view.component';

@Component({
  selector: 'app-clusters',
  templateUrl: './clusters.component.html',
  styleUrls: ['./clusters.component.scss']
})
export class ClustersComponent implements OnInit {

  constructor(private service: DocumentService) { }

  @Input() hideTitle: boolean = false;
  @Input() projectLink: string;

  serviceEndpoint = Links.CLUSTERS;
  clusterToDelete: any;
  deleteConfirmationAlert: string;

  selectedItem: any;

  @ViewChild('gridView') gridView:GridViewComponent;

  ngOnInit() {
  }

  get deleteConfirmationDescription(): string {
    return this.clusterToDelete && this.clusterToDelete.name
            && I18n.t('clusters.delete.confirmation', { clusterName:  this.clusterToDelete.name,
               interpolation: { escapeValue: false } } as I18n.TranslationOptions);
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

  getResourceLabel(b1, b2) {
    if (b1 == 0 || b2 ==0) {
      return b1 + ' of ' + b2;
    }
    let m = Utils.getMagnitude(b2);
    return Utils.formatBytes(b1, m) + ' of ' + Utils.formatBytes(b2, m) + Utils.magnitudes[m];
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
