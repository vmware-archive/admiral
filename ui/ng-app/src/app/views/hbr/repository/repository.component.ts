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

import { AuthService } from './../../../utils/auth.service';
import { Utils } from './../../../utils/utils';
import { ProjectService } from './../../../utils/project.service';
import { TagClickEvent } from 'harbor-ui';
import { Router, ActivatedRoute } from '@angular/router';
import { Component } from '@angular/core';
import { Roles } from './../../../utils/roles';

@Component({
  template: `
    <div class="main-view">
      <div class="title">Project Repositories</div>
      <hbr-repository-stackview [projectId]="projectId" [projectName]="projectName" [hasSignedIn]="true"
        [hasProjectAdminRole]="hasProjectAdminRole" (tagClickEvent)="watchTagClickEvent($event)"
        style="display: block;"></hbr-repository-stackview>

      <navigation-container>
        <router-outlet></router-outlet>
      </navigation-container>
    </div>
  `
})
export class RepositoryComponent {

  private static readonly HBR_DEFAULT_PROJECT_INDEX: Number = 1;
  private static readonly CUSTOM_PROP_PROJECT_INDEX: string = '__projectIndex';
  private userSecurityContext: any;

  sessionInfo = {};

  constructor(private router: Router, private route: ActivatedRoute,
    private ps: ProjectService, authService: AuthService) {
    authService.getCachedSecurityContext().then((securityContext) => {
      this.userSecurityContext = securityContext;
    }).catch((ex) => {
      console.log(ex);
    });
  }

  get projectId(): Number {
    let selectedProject = this.getSelectedProject();
    let projectIndex = selectedProject && Utils.getCustomPropertyValue(
      selectedProject.customProperties,
      RepositoryComponent.CUSTOM_PROP_PROJECT_INDEX);
    return (projectIndex && Number(projectIndex)) || RepositoryComponent.HBR_DEFAULT_PROJECT_INDEX;
  }

  get projectName(): string {
    let selectedProject = this.getSelectedProject();
    return (selectedProject && selectedProject.name) || 'unknown';
  }

  get hasProjectAdminRole(): boolean {
    let selectedProject = this.getSelectedProject();
    let projectLink = selectedProject && selectedProject.documentSelfLink;
    return Utils.isAccessAllowed(this.userSecurityContext, projectLink, [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
  }

  private getSelectedProject(): any {
    return this.ps && this.ps.getSelectedProject();
  }

  watchTagClickEvent(tag: TagClickEvent) {
    this.router.navigate(['repositories', tag.repository_name, 'tags', tag.tag_name], { relativeTo: this.route });
  }
}