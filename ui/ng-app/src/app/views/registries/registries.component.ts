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

import { Component, ViewEncapsulation } from '@angular/core';
import { AuthService } from "../../utils/auth.service";
import { ProjectService } from "../../utils/project.service";
import { FT } from '../../utils/ft';
import { Roles } from "../../utils/roles";
import { Utils } from "../../utils/utils";

@Component({
  selector: 'app-registries',
  templateUrl: './registries.component.html',
  styleUrls: ['./registries.component.scss'],
  encapsulation: ViewEncapsulation.None
})
/**
 * Registries main view.
 */
export class RegistriesComponent {

    isHbrEnabled = FT.isHbrEnabled();
    userSecurityContext: any;

    constructor(private projectService: ProjectService, private authService: AuthService) {

          if (!FT.isApplicationEmbedded()) {
              this.authService.getCachedSecurityContext().then((securityContext) => {
                  this.userSecurityContext = securityContext;

              }).catch((ex) => {
                  console.log(ex);
              });
          }
      }

    private getSelectedProject(): any {
        return this.projectService && this.projectService.getSelectedProject();
    }

    get hasProjectAdminRole(): boolean {
        let selectedProject = this.getSelectedProject();
        let projectSelfLink = selectedProject && selectedProject.documentSelfLink;

        return Utils.isAccessAllowed(this.userSecurityContext, projectSelfLink,
                                    [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
    }
}
