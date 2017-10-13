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

import {Component, Input, OnInit} from '@angular/core';
import { Utils } from '../../../utils/utils';
import { FT } from '../../../utils/ft';
import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-summary',
  templateUrl: './cluster-summary.component.html',
  styleUrls: ['./cluster-summary.component.scss']
})

/**
 *  A cluster's summary view.
 */
export class ClusterSummaryComponent implements OnInit {

  @Input() cluster: any;

  get documentId() {
    return this.cluster && Utils.getDocumentId(this.cluster.documentSelfLink);
  }

  get adminPortal() {
    if (this.cluster && this.cluster.address) {
      let urlParts = Utils.getURLParts(this.cluster.address);
      return 'https://' + urlParts.host + ':2378';
    }
    return '';
  }

  get publicAddress() {
    return this.cluster && this.cluster.publicAddress;
  }

  get clusterOverviewTextKey() {
    if (FT.isVic()) {
      return 'clusters.summary.clusterOverviewVic';
    }
    return 'clusters.summary.clusterOverview';
  }

  get clusterResourcesTextKey() {
    if (FT.isVic()) {
      return 'clusters.summary.clusterResourcesVic';
    }
    return 'clusters.summary.clusterResources';
  }

  get clusterStatus() {
    if (this.cluster) {
      return I18n.t(this.cluster.status);
    }
    return '';
  }

  get clusterState() {
    if (this.cluster) {
      return I18n.t('clusters.state.' + this.cluster.status);
    }
    return '';
  }

  ngOnInit() {
    // DOM init
  }

  formatNumber(number) {
    if (!number){
      return '0';
    }
    let m = Utils.getMagnitude(number);
    return Utils.formatBytes(number, m) + ' ' + Utils.magnitudes[m];
  }
}
