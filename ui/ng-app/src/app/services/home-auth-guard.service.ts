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

import { Injectable, Input } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { Links } from './../utils/links';
import { AuthService } from './../utils/auth.service';
import { Utils } from './../utils/utils';
import { FT } from './../utils/ft';
import { Roles } from './../utils/roles';
import { ProjectService } from './../utils/project.service';
import { DocumentService } from './../utils/document.service';

@Injectable()
export class HomeAuthGuard implements CanActivate {

  constructor(private router: Router, 
    private authService: AuthService, 
    private ps: ProjectService,
    private ds: DocumentService) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
    return new Promise((resolve, reject) => {
      if (!route.data["roles"]) {
        return reject(new Error("Roles not provided!"));
      }

      let roles = route.data["roles"] as Array<string>;
      let path = route.url[0].path;

      if (FT.isApplicationEmbedded()) {
        return resolve(true);
      }
      
      // First check for system roles.
      this.authService.getCachedSecurityContext().then((securityContext) => {
        if (securityContext && securityContext.roles) {
          for (var index = 0; index < securityContext.roles.length; index++) {
            var role = securityContext.roles[index];
            if (roles.indexOf(role) != -1) {
              return resolve(true);
            }
          }
        }

        if (!securityContext || !securityContext.projects) {
          return resolve(false);
        }

        let selectedProject = this.ps.getSelectedProject();
        let selectedProjectLink;
        if (selectedProject) {
          selectedProjectLink = this.ps.getSelectedProject().documentSelfLink;
        }
        let foundProject = securityContext.projects.find(p => p.documentSelfLink === selectedProjectLink);
        // If selected project is not valid for the principal, select next available.
        if (!foundProject && selectedProjectLink) {
          this.selectNextAvailableProject();
          selectedProjectLink = this.ps.getSelectedProject();
          foundProject = securityContext.projects.find(p => p.documentSelfLink === selectedProjectLink);
        }

        if (!foundProject) {
          return resolve(false);
        }
        // Navigating to home will redirect to applications, but if we are project viewer only we
        // should redirected to project-repositories as it is the only accessible view for this role.
        if (path === "applications") {
          if (this.isProjectViewerOnly(foundProject)) {
            this.router.navigate(["/home/project-repositories"]);
          }
        }
        
        let authorized = false;
        foundProject.roles.forEach(role => {
          if (roles.indexOf(role) != -1) {
            authorized = true;
          }
        });
        return resolve(authorized);
      })
      .catch((err) => {
        // allow access in case of no authentication
        return resolve(true);
      });
    });
  }

  private isProjectViewerOnly(project: any) {
    if (project && project.roles) {
      if (project.roles.indexOf(Roles.PROJECT_ADMIN) == -1 && project.roles.indexOf(Roles.PROJECT_MEMBER) == -1) {
        return true;
      }
    }
    return false;
  }

  private selectNextAvailableProject() {
    this.ds.listProjects().then(projects => {
      if (projects.documents && projects.documents.length > 0) {
        this.ps.setSelectedProject(projects.documents.pop());
      }
    });
  }
}