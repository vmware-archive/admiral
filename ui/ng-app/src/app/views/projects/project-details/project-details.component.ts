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

import { Component } from '@angular/core';
import { BaseDetailsComponent } from './../../../components/base/base-details.component';
import { DocumentService } from './../../../utils/document.service';
import { ActivatedRoute } from '@angular/router';
import { Links } from './../../../utils/links';
import { TagClickEvent } from 'harbor-ui';
import { RoutesRestriction } from './../../../utils/routes-restriction';
import { FT } from './../../../utils/ft';
import { Router } from '@angular/router';

@Component({
  selector: 'app-project-details',
  templateUrl: './project-details.component.html',
  styleUrls: ['./project-details.component.scss']
})
export class ProjectDetailsComponent extends BaseDetailsComponent {

  hbrProjectId;
  hbrSessionInfo = {};
  router: Router;
  isHbrEnabled = FT.isHbrEnabled();

  constructor(route: ActivatedRoute, service: DocumentService, router: Router) {
    super(route, service, Links.PROJECTS);
    this.router = router;
  }

  get projectName(): string {
    return (this.entity && this.entity.name) || 'unknown';
  }

  protected entityInitialized() {
    let cs = this.entity.customProperties || {};
    if (cs.__projectIndex) {
      this.hbrProjectId = parseInt(cs.__projectIndex);
    }
  }

  watchTagClickEvent(tag: TagClickEvent) {
    this.router.navigate(['repositories', tag.repository_name, 'tags', tag.tag_name], {relativeTo: this.route});
  }

  get projectsByIdRouteRestriction() {
    return RoutesRestriction.PROJECTS_ID;
  }
}
