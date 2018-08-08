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

import { Component, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from "../../../utils/error.service";
import { ProjectService } from "../../../utils/project.service";
import { Links } from '../../../utils/links';

@Component({
  selector: 'deployment-details',
  templateUrl: './deployment-details.component.html',
  styleUrls: ['./deployment-details.component.scss'],
  encapsulation: ViewEncapsulation.None
})
/**
 * Details view for a single deployment.
 */
export class DeploymentDetailsComponent extends BaseDetailsComponent {

  constructor(route: ActivatedRoute, router: Router, service: DocumentService,
              projectService: ProjectService, errorService: ErrorService) {

      super(Links.DEPLOYMENTS, route, router, service, projectService, errorService);
  }

  protected onProjectChange() {
      this.router.navigate(['../'], {relativeTo: this.route});
  }

  get deploymentSpecification() {
      return this.entity && this.entity.deployment.spec;
  }

  get containers() {
      return this.deploymentSpecification
                ? this.deploymentSpecification.template.spec.containers : [];
  }
}
