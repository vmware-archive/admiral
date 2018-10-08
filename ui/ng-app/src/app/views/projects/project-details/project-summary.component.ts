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

import { Component, Input } from '@angular/core';
import { AuthService } from '../../../utils/auth.service';
import { FT } from '../../../utils/ft';
import { ProjectService } from '../../../utils/project.service';
import { RoutesRestriction } from '../../../utils/routes-restriction';
import { Utils } from '../../../utils/utils';

@Component({
  selector: 'app-project-summary',
  templateUrl: './project-summary.component.html',
  styleUrls: ['./project-summary.component.scss']
})

/**
 *  A project's summary view.
 */
export class ProjectSummaryComponent {
    @Input() project: any;

    userSecurityContext: any;

    constructor(protected authService: AuthService, protected projectService: ProjectService) {

        if (!FT.isApplicationEmbedded()) {
            this.authService.getCachedSecurityContext().then((securityContext) => {
                this.userSecurityContext = securityContext;

            }).catch((ex) => {
                console.log(ex);
            });
        }
    }

  get documentId() {
    return this.project && Utils.getDocumentId(this.project.documentSelfLink);
  }

  get documentSelfLink() {
    return this.project && this.project.documentSelfLink;
  }

  get projectSummaryEditRestrictions() {
    return RoutesRestriction.PROJECT_SUMMARY_EDIT;
  }

  get clustersTextKey() {
    if (FT.isVic()) {
      return 'projects.summary.resources.clustersVic'
    }
    return 'projects.summary.resources.clusters'
  }

  get isAllowedEditProject() {
      let selectedProject = this.projectService.getSelectedProject();
      let projectSelfLink = selectedProject && selectedProject.documentSelfLink;

      return Utils.isAccessAllowed(this.userSecurityContext, projectSelfLink,
          RoutesRestriction.PROJECT_SUMMARY_EDIT);
  }
}
