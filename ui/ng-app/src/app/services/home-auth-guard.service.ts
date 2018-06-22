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

      // First check for system roles.
      this.authService.getCachedSecurityContext().then((securityContext) => {
        if (FT.isApplicationEmbedded()) {
          if (securityContext && securityContext.indexOf(Roles.VRA_CONTAINER_DEVELOPER) > -1 &&
                securityContext.indexOf(Roles.VRA_CONTAINER_ADMIN) == -1 && this.router.url === '/') {
            this.router.navigate(['/home/kubernetes/deployments']);
            return resolve(true);
          }
        }

        let securityContextRoles = FT.isApplicationEmbedded() ? securityContext : securityContext.roles;

        if (securityContextRoles) {
          for (var index = 0; index < securityContextRoles.length; index++) {
            var role = securityContextRoles[index];
            if (roles.indexOf(role) != -1) {
              return resolve(true);
            }
          }
        }

        if (!securityContext || !securityContext.projects) {
          return resolve(false);
        }

        let selectedProject = this.ps.getSelectedProject();
        // If there is still no project selected, select and do the check.
        if (!selectedProject) {
          this.selectProjectAndCheckRoles(securityContext, roles, path, resolve);
          return;
        }

        let selectedProjectLink = selectedProject.documentSelfLink;
        let foundProject = securityContext.projects.find(p => p.documentSelfLink === selectedProjectLink);
        // If selected project is not valid for the principal, select available and do the check.
        if (!foundProject) {
          this.selectProjectAndCheckRoles(securityContext, roles, path, resolve);
        } else {
          this.checkProjectRoles(foundProject, roles, path, resolve);
        }
      })
      .catch((err) => {
        // allow access in case of no authentication
        return resolve(true);
      });
    });
  }

  private checkProjectRoles(project: any, roles: any, path: any, resolve: any) {
    if (!project || !roles || roles.length < 1) {
      resolve(false);
    }
    let isAuthorized = false;
    project.roles.forEach(role => {
      if (roles.indexOf(role) != -1) {
        isAuthorized = true;
      }
    });

    // Navigating to home will redirect to applications, but if we are project viewer only we
    // should redirected to project-repositories as it is the only accessible view for this role.
    if (path === "applications") {
      if (this.isProjectViewerOnly(project)) {
        this.router.navigate(["/home/project-repositories"]);
      }
    }
    resolve(isAuthorized);
  }

  private selectProjectAndCheckRoles(securityContext: any, roles: any, path: any, resolve: any) {
    this.ds.listProjects().then(projects => {
      if (!projects.documents || projects.documents.length < 1) {
        resolve(false);
        return;
      }

      this.ps.setSelectedProject(projects.documents[0]);

      let selectedProject = this.ps.getSelectedProject();
      if (!selectedProject) {
        resolve(false);
        return;
      }
      let selectedProjectLink = selectedProject.documentSelfLink;
      let foundProject = securityContext.projects.find(p => p.documentSelfLink === selectedProjectLink);
      if (!foundProject) {
        resolve(false);
        return;
      }

      this.checkProjectRoles(foundProject, roles, path, resolve);
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
        this.ps.setSelectedProject(projects.documents[0]);
      }
    });
  }
}