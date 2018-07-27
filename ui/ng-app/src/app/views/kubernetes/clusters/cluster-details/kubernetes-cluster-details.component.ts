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

import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import 'rxjs/add/operator/switchMap';
import { BaseDetailsComponent } from '../../../../components/base/base-details.component';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from '../../../../utils/project.service';
import { Links } from '../../../../utils/links';
import { Utils } from '../../../../utils/utils';


@Component({
  selector: 'app-kubernetes-cluster-details',
  templateUrl: './kubernetes-cluster-details.component.html',
  styleUrls: ['./kubernetes-cluster-details.component.scss']
})
export class KubernetesClusterDetailsComponent extends BaseDetailsComponent
                                               implements OnInit, OnDestroy {

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

  protected entityInitialized() {
  }
}
