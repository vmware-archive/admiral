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
import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Router, NavigationEnd } from '@angular/router';
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

    formerViewPaths = {
      'templates': 'templates?$category=templates',
      'public-repositories': 'templates?$category=images',
      'closures': 'templates?$category=closures',
      'placements': 'placements',
      'hosts': 'hosts',
      'applications': 'applications',
      'containers': 'containers',
      'networks': 'networks',
      'volumes': 'volumes'
    }

    formerViewPath;

    selectedProject;
    projects;

    constructor(private router: Router, private ps: ProjectService) {
      this.routeObserve = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) {
          let formerViewPath;
          if (event.urlAfterRedirects.startsWith("/home/")) {
            let url = event.urlAfterRedirects.replace("/home/", "");
            for (let key in this.formerViewPaths) {
              if (url.startsWith(key)) {
                formerViewPath = this.formerViewPaths[key]
              }
            }
          }

          this.formerViewPath = formerViewPath;
        }
      });
    }

    ngOnInit() {
      this.ps.list().then((result) => {
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
    }

    selectProject(project) {
      this.selectedProject = project
      this.ps.setSelectedProject(this.selectedProject);
    }
}
