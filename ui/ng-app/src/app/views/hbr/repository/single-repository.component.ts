/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../utils/auth.service';
import { ErrorService } from '../../../utils/error.service';
import { ProjectService } from '../../../utils/project.service';
import { Roles } from '../../../utils/roles';
import { Utils } from '../../../utils/utils';

import { TagClickEvent } from '@harbor/ui';

@Component({
    template: `
        <div class="main-view" data-view-name="project-repository">
            <hbr-repository [projectId]="projectId" [repoName]="repoName" [hasSignedIn]="true"
                            [hasProjectAdminRole]="hasProjectAdminRole"
                            (tagClickEvent)="watchTagClickEvent($event)"
                            (backEvt)="backToRepoList($event)"
                            style="display: block;"></hbr-repository>
        </div>
    `
})
/**
 * Single Harbor repository view.
 */
export class SingleRepositoryComponent implements OnDestroy {
    private static readonly HBR_DEFAULT_PROJECT_INDEX: Number = 1;
    private static readonly CUSTOM_PROP_PROJECT_INDEX: string = '__projectIndex';

    private userSecurityContext: any;

    sessionInfo = {};

    repoName: string;
    private sub: Subscription = null;


    constructor(private router: Router, private route: ActivatedRoute,
                private projectService: ProjectService, authService: AuthService,
                private errorService: ErrorService) {

        authService.getCachedSecurityContext().then((securityContext) => {
            this.userSecurityContext = securityContext;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });

        this.sub = this.route.params.subscribe(params => {
            this.repoName = params['rid'];
        });
    }

    ngOnDestroy() {
        this.sub.unsubscribe();
    }

    get projectId(): Number {
        let selectedProject = this.getSelectedProject();

        let projectIndex = selectedProject && Utils.getCustomPropertyValue(
            selectedProject.customProperties, SingleRepositoryComponent.CUSTOM_PROP_PROJECT_INDEX);

        return (projectIndex && Number(projectIndex))
            || SingleRepositoryComponent.HBR_DEFAULT_PROJECT_INDEX;
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

    watchTagClickEvent(tag: TagClickEvent) {
        this.router.navigate(['tags', tag.tag_name],
            {relativeTo: this.route});
    }

    backToRepoList($event) {
        let path: any[] = Utils.getPathUp(this.router.url, 'hbrRepo');

        this.router.navigate(path, {relativeTo: this.route});
    }
}
