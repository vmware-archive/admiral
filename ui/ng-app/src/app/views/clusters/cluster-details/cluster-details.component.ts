/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import 'rxjs/add/operator/switchMap';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from "../../../utils/error.service";
import { ProjectService } from '../../../utils/project.service';
import { Links } from '../../../utils/links';
import { Utils } from '../../../utils/utils';

@Component({
  selector: 'app-cluster-details',
  templateUrl: './cluster-details.component.html',
  styleUrls: ['./cluster-details.component.scss']
})
export class ClusterDetailsComponent extends BaseDetailsComponent
                                     implements OnInit, OnDestroy {

  private sub: any;
  private resourceTabSelected:boolean = false;

  constructor(route: ActivatedRoute, router: Router, service: DocumentService,
              errorService: ErrorService, protected projectService: ProjectService) {
    super(Links.CLUSTERS, route, router, service, errorService);

    Utils.subscribeForProjectChange(projectService, (changedProjectLink) => {
      let currentProjectLink = this.projectLink;
      this.projectLink = changedProjectLink;

      if (currentProjectLink && currentProjectLink !== this.projectLink) {
        this.router.navigate(['../../'], {relativeTo: this.route});
      }
    });
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

  get showBackLink() {
      let urlSegments = this.route.snapshot.url;

      let clusterInProjectAdminView = urlSegments.find((urlSegment) => {
          return urlSegment.path.indexOf('projects') > -1;
      });

      // the cluster details are shown as part of projects view in administration
      return !!clusterInProjectAdminView;
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

  clickedSummaryTab() {
      this.resourceTabSelected = false;
  }

  clickedResourcesTab() {
      this.resourceTabSelected = true;
  }
}
