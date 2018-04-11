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

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from "rxjs/Subscription";
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { AuthService } from '../../../utils/auth.service';
import { DocumentService } from '../../../utils/document.service';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Roles } from '../../../utils/roles';
import { RoutesRestriction } from '../../../utils/routes-restriction';
import { Utils } from '../../../utils/utils';


@Component({
  selector: 'app-project-details',
  templateUrl: './project-details.component.html',
  styleUrls: ['./project-details.component.scss']
})
/**
 * Project Details tabbed view.
 */
export class ProjectDetailsComponent extends BaseDetailsComponent {
  hbrProjectId;
  hbrSessionInfo = {};
  isHbrEnabled = FT.isHbrEnabled();
  userSecurityContext: any;

  // tabs preselection through routing
  routeTabParamSubscription:Subscription;
  activatedTab: ProjectDetailsTab;

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

  ngOnInit() {
      super.ngOnInit();

      this.routeTabParamSubscription = this.route.params.subscribe((params) => {
          this.activatedTab = params['tab'];
      });
  }

  ngOnDestroy() {
      super.ngOnDestroy();

      this.routeTabParamSubscription.unsubscribe();
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

  get isActiveTabInfrastructure(): boolean {
    return this.activatedTab === ProjectDetailsTab.Infra;
  }

  get isActiveTabRegistries(): boolean {
    return this.activatedTab === ProjectDetailsTab.Registries;
  }

  get isActiveTabProjectRepositories(): boolean {
    return this.activatedTab === ProjectDetailsTab.Repositories;
  }

  infraTabActivated() {
    this.activatedTab = ProjectDetailsTab.Infra;
  }

  registriesTabActivated() {
    this.activatedTab = ProjectDetailsTab.Registries;
  }

  watchRepoClickEvent(repositoryItem) {
    this.router.navigate(['repositories', repositoryItem.name], { relativeTo: this.route });
  }

  reloadProject(project: any) {
    if (project) {
      this.entity = project;
    }
  }

  get hasProjectAdminRole(): boolean {
    return Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink,
                                    [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
  }

  get admiralProjectSelfLink() {
    return this.entity && this.entity.documentSelfLink;
  }

  get projectsByIdRouteRestriction() {
    return RoutesRestriction.PROJECTS_ID;
  }

  get hasAccessToRegistryReplication() {
    return Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink,
                                                RoutesRestriction.PROJECT_REGISTRY_REPLICATION);
  }

  get embedded(): boolean {
    return FT.isApplicationEmbedded();
  }
}

export enum ProjectDetailsTab {
  Infra = <any>'infra',
  Registries = <any>'registries',
  Repositories = <any>'hbrRepo'
}
