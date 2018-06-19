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

import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Router, NavigationEnd } from '@angular/router';
import { RoutesRestriction } from './../../utils/routes-restriction';
import { FormerViewPathBridge, RouteUtils } from './../../utils/route-utils';
import { AuthService } from './../../utils/auth.service';
import { DocumentService } from './../../utils/document.service';
import { ErrorService } from '../../utils/error.service';
import { ProjectService } from './../../utils/project.service';
import { Roles } from './../../utils/roles';
import { FT } from './../../utils/ft';

@Component({
  selector: 'app-main-resources',
  templateUrl: './main-resources.component.html',
  styleUrls: ['./main-resources.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComponent implements OnInit, OnDestroy {
    // features
    embeddedMode = FT.isApplicationEmbedded();

    isPksEnabled = FT.isPksEnabled();
    externalKubernetesEnabled = FT.isExternalKubernetesEnabled();

    isHbrEnabled = FT.isHbrEnabled();
    areClosuresAllowed = FT.areClosuresAllowed();
    showHostsView = FT.showHostsView();

    routeObserve: Subscription;
    errorObserve: Subscription;

    formerViewPaths = [
      new FormerViewPathBridge('/home/templates/image', '/templates/image'),
      new FormerViewPathBridge('/home/templates/template', '/templates/template'),
      new FormerViewPathBridge('/home/templates','/templates','$category=templates'),
      new FormerViewPathBridge('/home/public-repositories','/templates','$category=images'),
      new FormerViewPathBridge('/home/closure-definitions','/templates','$category=closures'),
      new FormerViewPathBridge('/home/closure-definitions','/templates','$category=closures'),
      new FormerViewPathBridge('/home/placements','/placements'),
      new FormerViewPathBridge('/home/hosts','/hosts'),
      new FormerViewPathBridge('/home/applications','/applications'),
      new FormerViewPathBridge('/home/containers','/containers'),
      new FormerViewPathBridge('/home/networks','/networks'),
      new FormerViewPathBridge('/home/volumes','/volumes'),
      new FormerViewPathBridge('/home/closures','/closures')
    ];

    formerViewPath;

    selectedProject;
    projects;
    showLibrary: boolean;
    showKubernetes: boolean;

    alertMessage: string;

    constructor(private router: Router, private documentService: DocumentService,
                private projectService: ProjectService, private errorService: ErrorService,
                private authService: AuthService) {

      this.routeObserve = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) {

          this.formerViewPath =
              RouteUtils.toFormerViewPath(event.urlAfterRedirects, this.formerViewPaths);
        }
      });

      this.errorObserve = this.errorService.errorMessages.subscribe((event) => {
          this.alertMessage = event;
      });


      this.projectService.activeProject.subscribe((value) => {
        // reload former view iframe
        var iframeFormerView =
            document.querySelector(".former-view > iframe:first-of-type");
        if (iframeFormerView) {
          var iWindow = (<HTMLIFrameElement> iframeFormerView).contentWindow.location.reload();
        }
      });
    }

    ngOnInit() {
      this.documentService.listProjects().then((result) => {
        this.projects = result.documents;

        if (!this.projects || this.projects.length === 0) {
          return;
        }

        this.sortProjects();

        this.selectedProject = null;
        let localProject = this.projectService.getSelectedProject();

        if (localProject) {
          this.projects.forEach(project => {

            if ((project.documentSelfLink
                    && project.documentSelfLink === localProject.documentSelfLink)
                || (project.id && project.id === localProject.id)) {

              this.selectedProject = project;
            }
          });
        }

        if (!this.selectedProject) {
          this.selectedProject = this.projects[0];
        }

        this.projectService.setSelectedProject(this.selectedProject);

        this.checkShowLibrary();
        this.checkShowKubernetes();
      });
    }

    ngOnDestroy() {
      this.routeObserve.unsubscribe();
      this.errorObserve.unsubscribe();
    }

    updateProjects() {
        this.documentService.listProjects().then((result) => {
            this.projects = result.documents;

            if (!this.projects || this.projects.length === 0) {
                return;
            }

            this.sortProjects();
        }).catch((e) => {
            console.error('Failed to update projects', e);
        })
    }

    selectProject(project) {
      this.selectedProject = project;
      this.projectService.setSelectedProject(this.selectedProject);

      this.checkShowLibrary();
    }

    onFormerViewRouteChange(newFormerPath: string) {
      if (!this.formerViewPath) {
        // not yet initialized
        return;
      }

      let viewPath = RouteUtils.fromFormerViewPath(newFormerPath, this.formerViewPaths);
      if (viewPath) {
        this.router.navigateByUrl(viewPath);
      }
    }

    resetAlert() {
        this.alertMessage = null;
    }

    sortProjects() {
      this.projects.sort((project1, project2) => {
        if (project1.name > project2.name) {
          return 1;
        }

        if (project1.name < project2.name) {
          return -1;
        }

        return 0;
      });
    }

    checkShowKubernetes() {
      if (FT.isApplicationEmbedded() && FT.isPksEnabled()) {
        this.authService.getCachedSecurityContext().then(securityContext => {
          // check if the user is only container developer
          if (securityContext && securityContext.indexOf(Roles.VRA_CONTAINER_DEVELOPER) > -1 &&
                securityContext.indexOf(Roles.VRA_CONTAINER_ADMIN) == -1) {
            this.showKubernetes = true;
          }
        });
      }
    }

    checkShowLibrary() {
      if (this.isHbrEnabled || !this.selectedProject || FT.isApplicationEmbedded()) {
        this.showLibrary = true;
        return;
      }

      let selectedProjectLink = this.selectedProject.documentSelfLink;
      let selectedProjectId = this.selectedProject.id;
      this.authService.getCachedSecurityContext().then(securityContext => {
        if (!securityContext) {
          this.showLibrary = false;
          return;
        }

        // If principal is cloud admin show it.
        if (securityContext.roles && securityContext.roles.indexOf(Roles.CLOUD_ADMIN) != -1) {
          this.showLibrary = true;
          return;
        }

        let foundProject = securityContext.projects.find(project => {
          return project.documentSelfLink === selectedProjectLink
                    || project.id === selectedProjectId;
        });

        if (foundProject && foundProject.roles) {
          this.showLibrary = foundProject.roles.indexOf(Roles.PROJECT_ADMIN) > -1
                                || foundProject.roles.indexOf(Roles.PROJECT_MEMBER) > -1;
        } else {
          this.showLibrary = false;
        }
      }).catch(e => {
        this.showLibrary = true;
      })
    }

    get deploymentsRouteRestriction() {
        return RoutesRestriction.DEPLOYMENTS;
    }

    get infrastructureRouteRestriction() {
        return RoutesRestriction.INFRASTRUCTURE;
    }

    get clustersRouteRestriction() {
        return RoutesRestriction.CLUSTERS;
    }

    get templatesRouteRestriction() {
        return RoutesRestriction.TEMPLATES;
    }

    get publicReposRouteRestriction() {
        return RoutesRestriction.PUBLIC_REPOSITORIES;
    }

    get currentProjectLink() {
        return (this.selectedProject)
                    && (this.selectedProject.documentSelfLink || this.selectedProject.id);
    }

    get navigationClustersTextKey() {
      if (FT.isVic()) {
        return "navigation.clustersVic";
      }
      return "navigation.clusters";
    }

    get endpointsRouteRestriction() {
        return RoutesRestriction.ENDPOINTS_MENU_VRA;
    }

    get identityManagementRouteRestriction() {
        return RoutesRestriction.IDENTITY_MANAGEMENT;
    }

    get registriesRouteRestriction() {
        return RoutesRestriction.REGISTRIES;
    }
}
