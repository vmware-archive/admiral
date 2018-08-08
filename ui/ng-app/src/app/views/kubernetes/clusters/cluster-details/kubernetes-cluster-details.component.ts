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

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../../components/base/base-details.component';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from "../../../../utils/error.service";
import { ProjectService } from '../../../../utils/project.service';
import { Links } from '../../../../utils/links';

@Component({
  selector: 'app-kubernetes-cluster-details',
  templateUrl: './kubernetes-cluster-details.component.html',
  styleUrls: ['./kubernetes-cluster-details.component.scss']
})
/**
 * Kubernetes cluster details view.
 */
export class KubernetesClusterDetailsComponent extends BaseDetailsComponent {

  constructor(route: ActivatedRoute, router: Router, service: DocumentService,
              errorService: ErrorService, projectService: ProjectService) {

    super(Links.CLUSTERS, route, router, service, projectService, errorService);
  }

  protected onProjectChange() {
      this.router.navigate(['../../'], {relativeTo: this.route});
  }
}
