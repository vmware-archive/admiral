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

import { Component, Input, ViewChild, Output, OnChanges, EventEmitter, SimpleChanges } from '@angular/core';
import { DocumentService } from "../../../utils/document.service";
import * as I18n from 'i18next';
import { Utils } from "../../../utils/utils";
import { GridViewComponent } from '../../../components/grid-view/grid-view.component';
import { constants } from '../../../utils/constants';

@Component({
  selector: 'app-cluster-resources',
  templateUrl: './cluster-resources.component.html',
  styleUrls: ['./cluster-resources.component.scss']
})
/**
 *  A cluster's resources view.
 */
export class ClusterResourcesComponent implements OnChanges {

  @Input() cluster: any;
  @Input() projectLink: string;
  @Output() onChange: EventEmitter<any> = new EventEmitter();
  @ViewChild('gridView') gridView: GridViewComponent;

  serviceEndpoint: string;

  showAddHost: boolean;

  hostToDelete: any;
  deleteConfirmationAlert: string;

  constructor(private service: DocumentService) { }

  ngOnChanges(changes: SimpleChanges) {
    this.serviceEndpoint = changes.cluster.currentValue.documentSelfLink + '/hosts';
  }

  get deleteConfirmationDescription(): string {
    return this.hostToDelete && I18n.t('hosts.actions.delete.confirmation',
            { hostName: this.getHostName(this.hostToDelete),
              interpolation: { escapeValue: false } } as I18n.TranslationOptions);
  }

  deleteHost(event, host) {
    event.stopPropagation();

    this.hostToDelete = host;
    return false; // prevents navigation
  }

  deleteConfirmed() {
    this.service.delete(this.serviceEndpoint + '/' + Utils.getDocumentId(this.hostToDelete.documentSelfLink), this.projectLink)
        .then(result => {
          this.hostToDelete = null;
          this.gridView.refresh();
        })
        .catch(err => {
          this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
  }

  deleteCanceled() {
    this.hostToDelete = null;
  }

  onAddHost() {
    this.showAddHost = true;
  }

  onAddHostCanceled() {
    this.showAddHost = false;
  }

  onHostAdded() {
    this.showAddHost = false;
    this.gridView.refresh();
  }

  getContainersCount(host) {
    let containersCount = Utils.getCustomPropertyValue(host.customProperties, '__Containers');
    return containersCount ? Math.round(containersCount) : 0;
  }

  getDocumentId(host) {
    return Utils.getDocumentId(host.documentSelfLink);
  }

  hostState(host) {
    return I18n.t('hosts.state.' + host.powerState);
  }

  getHostName(host) {
    return Utils.getHostName(host);
  }

  getCpuPercentage(host, shouldRound) {
    return Utils.getCpuPercentage(host, shouldRound);
  }

  getMemoryPercentage(host, shouldRound) {
    return Utils.getMemoryPercentage(host, shouldRound);
  }

  operationSupported(op, host) {
    if (op === 'ENABLE') {
      return host.powerState === constants.hosts.state.SUSPEND
          || host.powerState === constants.hosts.state.OFF;
    } else if (op === 'DISABLE') {
      return host.powerState !== constants.hosts.state.SUSPEND
          && host.powerState !== constants.hosts.state.OFF;
    }

    return true;
  }

  enableHost(event, host) {
    event.stopPropagation();

    this.service.patch(host.documentSelfLink, { 'powerState': constants.hosts.state.ON })
        .then(result => {
          this.gridView.refresh();
          this.onChange.emit();
        })
        .catch(err => {
          console.log(Utils.getErrorMessage(err)._generic);
        });

    return false; // prevents navigation
  }

  disableHost(event, host) {
    event.stopPropagation();

    this.service.patch(host.documentSelfLink, { 'powerState': constants.hosts.state.SUSPEND })
        .then(result => {
          this.gridView.refresh();
          this.onChange.emit();
        })
        .catch(err => {
          console.log(Utils.getErrorMessage(err)._generic);
        });

    return false; // prevents navigation
  }
}
