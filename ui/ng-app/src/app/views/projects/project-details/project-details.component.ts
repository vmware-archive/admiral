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

import { Component, ViewChild, Input, enableProdMode, ContentChild } from '@angular/core';
import { BaseDetailsComponent } from './../../../components/base/base-details.component';
import { DocumentService } from './../../../utils/document.service';
import { ActivatedRoute } from '@angular/router';
import { Links } from './../../../utils/links';
import { TagClickEvent } from 'harbor-ui';
import { RoutesRestriction } from './../../../utils/routes-restriction';
import { FT } from './../../../utils/ft';
import { Router } from '@angular/router';
import { Utils } from './../../../utils/utils';
import { AuthService } from './../../../utils/auth.service';
import { Roles } from './../../../utils/roles';

@Component({
  selector: 'app-project-details',
  templateUrl: './project-details.component.html',
  styleUrls: ['./project-details.component.scss']
})
export class ProjectDetailsComponent extends BaseDetailsComponent {

  hbrProjectId;
  hbrSessionInfo = {};
  isHbrEnabled = FT.isHbrEnabled();
  userSecurityContext: any;

  constructor(route: ActivatedRoute, service: DocumentService,
    private router: Router, private authService: AuthService) {
    super(route, service, Links.PROJECTS);

    if(!this.embedded) {
      this.authService.getCachedSecurityContext().then((securityContext) => {
          this.userSecurityContext = securityContext;
      }).catch((ex) => {
          console.log(ex);
      });
    }
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

  reloadProject(project: any) {
    if (project) {
      this.entity = project;
    }
  }

  get hasProjectAdminRole(): boolean {
    return Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink, [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
  }

  get admiralProjectSelfLink() {
    return this.entity && this.entity.documentSelfLink;
  }

  get projectsByIdRouteRestriction() {
    return RoutesRestriction.PROJECTS_ID;
  }

  get isRegistryReplicationReadOnly() {
    let accessAllowed = Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink, RoutesRestriction.PROJECT_REGISTRY_REPLICATION);
    return !accessAllowed;
  }

  get embedded(): boolean {
    return FT.isApplicationEmbedded();
  }
}
