import { Component, ViewChild, OnInit } from '@angular/core';
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

  serviceEndpoint = Links.CLUSTERS;
  clusterToDelete: any;
  deleteConfirmationAlert: string;

  @ViewChild('gridView') gridView:GridViewComponent;

  ngOnInit() {
  }

  get deleteConfirmationDescription(): string {
    return this.clusterToDelete && this.clusterToDelete.name
            && I18n.t('clusters.delete.confirmation',
            { clusterName:  this.clusterToDelete.name } as I18n.TranslationOptions);
  }

  deleteCluster(event, cluster) {
    this.clusterToDelete = cluster;
    event.stopPropagation();
    return false; // prevents navigation
  }

  deleteConfirmed() {
    this.service.delete(this.clusterToDelete.documentSelfLink)
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

  clusterState(cluster) {
    return I18n.t('clusters.state.' + cluster.status);
  }
}
