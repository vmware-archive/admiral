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

import { FT } from './../../utils/ft';
import { Roles } from './../../utils/roles';
import { Ajax } from './../../utils/ajax.service';
import { DocumentService } from './../../utils/document.service';
import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Router, NavigationEnd } from '@angular/router';

import { RoutesRestriction } from './../../utils/routes-restriction';
import { FormerViewPathBridge, RouteUtils } from './../../utils/route-utils';

import { AuthService } from './../../utils/auth.service';
import { ErrorService } from '../../utils/error.service';
import { ProjectService } from './../../utils/project.service';

@Component({
  selector: 'app-main-resources',
  templateUrl: './main-resources.component.html',
  styleUrls: ['./main-resources.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComponent implements OnInit, OnDestroy {

    kubernetesEnabled = FT.isKubernetesHostOptionEnabled();
    areClosuresAllowed = FT.areClosuresAllowed();

    embeddedMode = FT.isApplicationEmbedded();
    isHbrEnabled = FT.isHbrEnabled();

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

    alertMessage: string;

    constructor(private router: Router, private ds: DocumentService, private ajax: Ajax,
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
      this.ds.listProjects().then((result) => {
        this.projects = result.documents;

        if (!this.projects || this.projects.length === 0) {
          return;
        }

        this.sortProjects();

        this.selectedProject = null;
        let localProject = this.projectService.getSelectedProject();

        if (localProject) {
          this.projects.forEach(p => {
            if ((p.documentSelfLink && p.documentSelfLink === localProject.documentSelfLink) ||
              (p.id && p.id === localProject.id)) {
              this.selectedProject = p;
            }
          });
        }

        if (!this.selectedProject) {
          this.selectedProject = this.projects[0];
        }

        this.projectService.setSelectedProject(this.selectedProject);
        this.checkShowLibrary();
      });
    }

    ngOnDestroy() {
      this.routeObserve.unsubscribe();
      this.errorObserve.unsubscribe();
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
      this.projects.sort((x1, x2) => {
        if (x1.name > x2.name) {
          return 1;
        }

        if (x1.name < x2.name) {
          return -1;
        }

        return 0;
      });
    }

    checkShowLibrary() {
      if (this.isHbrEnabled || !this.selectedProject) {
        this.showLibrary = true;
        return;
      }

      let selectedProjectLink = this.selectedProject.documentSelfLink;
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

        let foundProject = securityContext.projects.find(x => {
          return x.documentSelfLink === selectedProjectLink;
        });

        if (foundProject && foundProject.roles) {
          if (foundProject.roles.indexOf(Roles.PROJECT_ADMIN) == -1
            && foundProject.roles.indexOf(Roles.PROJECT_MEMBER) == -1) {
              this.showLibrary = false;
            } else {
              this.showLibrary = true;
            }
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
        if (this.selectedProject) {
          return this.selectedProject.documentSelfLink;
        }
    }

    get navigationClustersTextKey() {
      if (FT.isVic()) {
        return "navigation.clustersVic";
      }
      return "navigation.clusters";
    }


}
