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

import { Component, OnInit, OnDestroy } from '@angular/core';
import { BaseDetailsComponent } from './../../../components/base/base-details.component';
import { DocumentService } from './../../../utils/document.service';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { Links } from './../../../utils/links';
import 'rxjs/add/operator/switchMap';

@Component({
  selector: 'app-cluster-details',
  templateUrl: './cluster-details.component.html',
  styleUrls: ['./cluster-details.component.scss']
})
export class ClusterDetailsComponent extends BaseDetailsComponent implements OnInit, OnDestroy {

  private sub: any;

  constructor(route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.CLUSTERS);
  }

  ngOnInit() {
    this.sub = this.route.params.subscribe(params => {
      let projectId = params['projectId'];
      if (projectId) {
        this.projectLink = Links.PROJECTS + '/' + projectId;
      }
      super.ngOnInit();
    });
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  protected entityInitialized() {
  }

  get showResources() {
    if (this.entity) {
      return this.entity.type == 'DOCKER';
    }
    return false;
  }

  reloadCluster() {
    this.service.get(this.entity.documentSelfLink, false, this.projectLink).then(cluster => {
      this.entity = cluster;
    });
  }
}
