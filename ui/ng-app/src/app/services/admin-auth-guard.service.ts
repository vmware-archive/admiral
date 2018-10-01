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

import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../utils/auth.service';
import { FT } from '../utils/ft';
import { Utils } from '../utils/utils';

@Injectable()
export class AdminAuthGuard implements CanActivate {

    constructor(protected router: Router, protected authService: AuthService) {
        //
    }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {

        return new Promise((resolve, reject) => {
            if (!route.data['roles']) {
                return reject(new Error('Roles not provided!'));
            }

            let roles = route.data['roles'] as Array<string>;

            // currently there are no need to check the VRA security context,
            // because this guard service is only used in VIC
            if (FT.isApplicationEmbedded()) {
                return resolve(true);
            }

            // First check for system roles.
            this.authService.getCachedSecurityContext().then((securityContext) => {

                // check for system roles
                let hasSystemRole = Utils.hasSystemRole(securityContext, roles);

                if (hasSystemRole) {
                    return resolve(true);
                }

                if (!securityContext || !securityContext.projects) {
                    return resolve(false);
                }

                for (let idxProject = 0; idxProject < securityContext.projects.length; idxProject++) {
                    let project = securityContext.projects[idxProject];

                    if (project && project.roles) {
                        if (state.url.indexOf(Utils.getDocumentId(project.documentSelfLink)) != -1) {

                            let authorized = false;
                            for (let idxRole = 0; idxRole < project.roles.length; idxRole++) {

                                let role = project.roles[idxRole];
                                if (roles.indexOf(role) != -1) {

                                    authorized = true;
                                }
                            }

                            return resolve(authorized);
                        }
                    }
                }

                let authorized = false;
                for (let idxProject = 0; idxProject < securityContext.projects.length; idxProject++) {
                    let project = securityContext.projects[idxProject];

                    if (project && project.roles) {
                        project.roles.forEach(role => {
                            if (roles.indexOf(role) != -1) {

                                authorized = true;
                            }
                        });
                    }
                }

                return resolve(authorized);

            }).catch((err) => {
                // allow access in case of no authentication

                return resolve(true);
            });
        });
    }
}
