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
import { ActivatedRoute, Router } from "@angular/router";
import { TabbedViewComponent } from "../../components/base/tabbed-view-component";
import { AuthService } from "../../utils/auth.service";
import { ProjectService } from "../../utils/project.service";
import { FT } from '../../utils/ft';
import { Roles } from "../../utils/roles";
import { Utils } from "../../utils/utils";

// tabs
const TAB_ID_REGISTRIES = "global";
const TAB_ID_ENDPOINTS = "endpoints";
const TAB_ID_REPLICATION = "replication";

@Component({
  selector: 'app-registries',
  templateUrl: './registries.component.html',
  styleUrls: ['./registries.component.scss'],
  encapsulation: ViewEncapsulation.None
})
/**
 * Registries main view.
 */
export class RegistriesComponent extends TabbedViewComponent {

    isHbrEnabled = FT.isHbrEnabled();
    userSecurityContext: any;

    constructor(private projectService: ProjectService, private authService: AuthService,
                route: ActivatedRoute, router: Router) {
        super(route, router);

        if (!FT.isApplicationEmbedded()) {
            this.authService.getCachedSecurityContext().then((securityContext) => {
                  this.userSecurityContext = securityContext;

            }).catch((ex) => {
                console.log(ex);
            });
        }

        this.supportedTabs = [TAB_ID_REGISTRIES, TAB_ID_ENDPOINTS, TAB_ID_REPLICATION];
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

    get isActiveTabRegistries() {
        return this.isActiveTab(TAB_ID_REGISTRIES);
    }

    registriesTabActivated($event) {
        this.tabActivated($event, TAB_ID_REGISTRIES);
    }

    get isActiveTabEndpoints() {
        return this.isActiveTab(TAB_ID_ENDPOINTS);
    }

    endpointsTabActivated($event) {
        this.tabActivated($event, TAB_ID_ENDPOINTS);
    }

    get isActiveTabReplication() {
        return this.isActiveTab(TAB_ID_REPLICATION);
    }

    replicationTabActivated($event) {
        this.tabActivated($event, TAB_ID_REPLICATION);
    }

    private navigateToRegistries($event) {
        this.tabActivated(true, TAB_ID_REGISTRIES);
    }
}
