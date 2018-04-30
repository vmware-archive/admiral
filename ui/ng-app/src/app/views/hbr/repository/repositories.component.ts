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

import { Component } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../utils/auth.service';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from "../../../utils/error.service";
import { ProjectService } from '../../../utils/project.service';

import { Constants } from "../../../utils/constants";
import { Roles } from '../../../utils/roles';
import { Utils } from '../../../utils/utils';
import { Links } from "../../../utils/links";

import * as I18n from "i18next";

@Component({
  template: `
      <div class="main-view" data-view-name="project-repositories">
          <clr-alert [clrAlertType]="alertType" [(clrAlertClosed)]="!alertMessage"
                     (clrAlertClosedChange)="resetAlert()">
              <div class="alert-item"><span class="alert-text">{{ alertMessage }}</span></div>
          </clr-alert>

          <div class="title">{{"navigation.internalRepositories" | i18n}}</div>

          <hbr-repository-gridview [projectId]="projectId" [projectName]="projectName" 
                                   [hasSignedIn]="true" [hasProjectAdminRole]="hasProjectAdminRole"
                                   (repoClickEvent)="watchRepoClickEvent($event)"
                                   (repoProvisionEvent)="watchRepoProvisionEvent($event)"
                                   (addInfoEvent)="watchAddInfoEvent($event)"
          ></hbr-repository-gridview>
      </div>
  `
})
/**
 * Harbor repositories list view.
 */
export class RepositoriesComponent {
    private static readonly HBR_DEFAULT_PROJECT_INDEX: Number = 1;
    private static readonly CUSTOM_PROP_PROJECT_INDEX: string = '__projectIndex';

    private userSecurityContext: any;

    sessionInfo = {};

    alertMessage: string;
    alertType: any;

    constructor(private router: Router, private route: ActivatedRoute,
              private projectService: ProjectService, authService: AuthService,
              private documentService: DocumentService, private errorService: ErrorService) {

        authService.getCachedSecurityContext().then((securityContext) => {
            this.userSecurityContext = securityContext;

        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    get projectId(): Number {
        let selectedProject = this.getSelectedProject();

        let projectIndex = selectedProject && Utils.getCustomPropertyValue(
                selectedProject.customProperties, RepositoriesComponent.CUSTOM_PROP_PROJECT_INDEX);

        return (projectIndex && Number(projectIndex))
            || RepositoriesComponent.HBR_DEFAULT_PROJECT_INDEX;
    }

    get projectName(): string {
        let selectedProject = this.getSelectedProject();

        return (selectedProject && selectedProject.name) || 'unknown';
    }

    get hasProjectAdminRole(): boolean {
        let selectedProject = this.getSelectedProject();
        let projectLink = selectedProject && selectedProject.documentSelfLink;

        return Utils.isAccessAllowed(this.userSecurityContext, projectLink,
                                [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
    }

    private getSelectedProject(): any {
        return this.projectService && this.projectService.getSelectedProject();
    }

    watchRepoClickEvent(repositoryItem) {
        this.router.navigate(['repositories', repositoryItem.name],
                                { relativeTo: this.route });
    }

    watchRepoProvisionEvent(repositoryItem) {
        var name = Utils.getDocumentId(repositoryItem.name);

        var containerDescription = {
            image: repositoryItem.name,
            name: name,
            publishAll: true
        };

        var currentComponent = this;
        var startedRequest:any;
        this.documentService.post(Links.CONTAINER_DESCRIPTIONS, containerDescription)
            .then((createdContainerDescription) => {

                var request:any = {};
                request.resourceType = 'DOCKER_CONTAINER';
                request.resourceDescriptionLink = createdContainerDescription.documentSelfLink;

                request.tenantLinks = createdContainerDescription.tenantLinks;

                this.documentService.post(Links.REQUESTS, request).then(function(createdRequest) {
                    startedRequest = createdRequest;
                    console.log('Provisioning request started', request);

                    // Show show alert message - the request has been triggered
                    currentComponent.alertType = Constants.alert.type.SUCCESS;
                    currentComponent.alertMessage = I18n.t('projectRepositories.containerDeploymentStarted');
                }).catch(error => {
                    console.log('Failed to start container provisioning', error);
                    currentComponent.errorService.error(Utils.getErrorMessage(error)._generic);
                });
            }).catch(error => {
                console.log('Failed to create container description', error);
                currentComponent.errorService.error(Utils.getErrorMessage(error)._generic);
            });
    }

    watchAddInfoEvent(repositoryItem) {
        // Navigate to Container Definition screen
        this.router.navigate(['/home/templates/image/' + repositoryItem.name + '/newContainer']);
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
