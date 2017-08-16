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
import { Ajax } from './../../utils/ajax.service';
import { Links } from './../../utils/links';
import { DocumentListResult, DocumentService } from './../../utils/document.service';
import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Router, NavigationEnd } from '@angular/router';
import { ProjectService } from './../../utils/project.service';
import { ErrorService } from '../../utils/error.service';
import { FormerViewPathBridge, RouteUtils } from './../../utils/route-utils';

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
      new FormerViewPathBridge('/home/unikernels', '/templates', '$category=templates'),
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

    alertMessage: string;

    constructor(private router: Router, private ds: DocumentService, private ajax: Ajax,
                private ps: ProjectService, private errorService: ErrorService) {

      this.routeObserve = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) {

          this.formerViewPath =
              RouteUtils.toFormerViewPath(event.urlAfterRedirects, this.formerViewPaths);
        }
      });

      this.errorObserve = this.errorService.errorMessages.subscribe((event) => {
          this.alertMessage = event;
      });
    }

    ngOnInit() {
      this.listProjects().then((result) => {
        this.projects = result.documents;

        if (!this.projects || this.projects.length === 0) {
          return;
        }

        this.selectedProject = null;
        let localProject = this.ps.getSelectedProject();

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

        this.ps.setSelectedProject(this.selectedProject);
      });
    }

    ngOnDestroy() {
      this.routeObserve.unsubscribe();
      this.errorObserve.unsubscribe();
    }

    listProjects() {
      if (FT.isApplicationEmbedded()) {
        return this.ajax.get(Links.GROUPS, null).then(result => {
          let documents = result || [];
          return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
        });
      } else {
        return this.ds.list(Links.PROJECTS, null);
      }
    }

    selectProject(project) {
      this.selectedProject = project;
      this.ps.setSelectedProject(this.selectedProject);
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
}

