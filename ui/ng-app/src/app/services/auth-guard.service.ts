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

import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { Links } from './../utils/links';
import { AuthService } from './../utils/auth.service';

@Injectable()
export class AuthGuard implements CanActivate {
  constructor(private router: Router, private authService: AuthService) {
  }

  canActivate(route: ActivatedRouteSnapshot): Promise<boolean> {
    return new Promise((resolve, reject) => {
      if (!route.data["roles"]) {
        return reject(new Error("Roles not provided!"));
      }

      let roles = route.data["roles"] as Array<string>;
      this.authService.loadCurrentUserSecurityContext().then((securityContext) => {
        let authorized = false;
        if (securityContext && securityContext.roles) {
          securityContext.roles.forEach(element => {
            if (roles.indexOf(element) != -1) {
              authorized = true;
              return;
            }
          });
        }

        if (securityContext && securityContext.projects) {
          securityContext.projects.forEach(project => {
            if (project &&  project.roles) {
              project.roles.forEach(role => {
                if (roles.indexOf(role) != -1) {
                  authorized = true;
                  return;
                }
              });
            }
          });
        }

        authorized ? resolve(true) : resolve(false);
      })
      .catch((err) => {
        // allow access in case of no authentication
        return resolve(true);
      });
    });
  }
}