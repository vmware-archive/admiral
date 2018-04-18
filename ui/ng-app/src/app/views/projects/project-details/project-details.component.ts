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
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from "rxjs/Subscription";
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { AuthService } from '../../../utils/auth.service';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from "../../../utils/error.service";
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Roles } from '../../../utils/roles';
import { RoutesRestriction } from '../../../utils/routes-restriction';
import { Utils } from '../../../utils/utils';

const TAB_ID_SUMMARY = "summary";
const TAB_ID_MEMBERS = "members";
const TAB_ID_REPOSITORIES = "hbrRepo";
const TAB_ID_INFRASTRUCTURE = "infra";
const TAB_ID_REGISTRIES = "registries";
const TAB_ID_REPLICATION = "replication";
const TAB_ID_CONFIGURATION = "config";
const TAB_ID_NONE = "";

@Component({
  selector: 'app-project-details',
  templateUrl: './project-details.component.html',
  styleUrls: ['./project-details.component.scss']
})
/**
 * Project Details tabbed view.
 */
export class ProjectDetailsComponent extends BaseDetailsComponent {

    hbrProjectId;
    hbrSessionInfo = {};
    isHbrEnabled = FT.isHbrEnabled();
    userSecurityContext: any;

    // tabs preselection through routing
    routeTabParamSubscription:Subscription;
    activatedTab: string = TAB_ID_NONE;
    tabIdForTabViews: string = TAB_ID_NONE;

    constructor(route: ActivatedRoute, service: DocumentService, private router: Router,
                private authService: AuthService, errorService: ErrorService) {
        super(route, service, Links.PROJECTS);

        if (!this.embedded) {
            this.authService.getCachedSecurityContext().then((securityContext) => {
                this.userSecurityContext = securityContext;
            }).catch((error) => {
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            });
        }
    }

    ngOnInit() {
        super.ngOnInit();

        this.routeTabParamSubscription = this.route.params.subscribe((params) => {
            this.activatedTab = params['tab'];
            if (this.activatedTab) {
                this.tabIdForTabViews = TAB_ID_NONE;
            }
        });
    }

    ngOnDestroy() {
        super.ngOnDestroy();

        this.routeTabParamSubscription.unsubscribe();
    }

    get projectName(): string {
      return (this.entity && this.entity.name) || 'unknown';
    }

    protected entityInitialized() {
        let cs = this.entity.customProperties || {};
        if (cs.__projectIndex) {
            this.hbrProjectId = parseInt(cs.__projectIndex);
        }
    }

    get isActiveTabSummary(): boolean {
        return this.activatedTab === TAB_ID_SUMMARY;
    }

    summaryTabActivated($event) {
        this.routeTab($event, TAB_ID_SUMMARY);
    }

    get isActiveTabMembers(): boolean {
        return this.activatedTab === TAB_ID_MEMBERS;
    }

    membersTabActivated($event) {
        this.routeTab($event, TAB_ID_MEMBERS);
    }

    get isActiveTabRepositories(): boolean {
        return this.activatedTab === TAB_ID_REPOSITORIES;
    }

    repositoriesTabActivated($event) {
        this.routeTab($event, TAB_ID_REPOSITORIES);
    }

    get isActiveTabInfrastructure(): boolean {
        return this.activatedTab === TAB_ID_INFRASTRUCTURE;
    }

    infrastructureTabActivated($event) {
        this.routeTab($event, TAB_ID_INFRASTRUCTURE);
    }

    get isActiveTabRegistries(): boolean {
        return this.activatedTab === TAB_ID_REGISTRIES;
    }

    registriesTabActivated($event) {
        this.routeTab($event, TAB_ID_REGISTRIES);
    }

    get isActiveTabReplication() {
        return this.activatedTab === TAB_ID_REPLICATION;
    }

    replicationTabActivated($event) {
        this.routeTab($event, TAB_ID_REPLICATION);
    }

    get isActiveTabConfig() {
        return this.activatedTab === TAB_ID_CONFIGURATION;
    }

    configTabActivated($event) {
        this.routeTab($event, TAB_ID_CONFIGURATION);
    }

    routeTab(isActivated, currentTab) {
        let urlSegments = this.route.snapshot.url;

        if (isActivated) {
            // tab selection has changed
            if (this.activatedTab !== currentTab) {
                // is previous tab still present in route
                let prevTabString = this.activatedTab;
                let prevTabUrlSegment = urlSegments.find((urlSegment) => {
                    return urlSegment.path.indexOf(prevTabString) > -1;
                });

                let path = '';
                if (prevTabUrlSegment) {
                    path += '../';
                }

                this.activatedTab = currentTab;

                let activeTabString = this.activatedTab;
                let activeTabUrlSegment = urlSegments.find((urlSegment) => {
                    return urlSegment.path.indexOf(activeTabString) > -1;
                });

                if (!prevTabUrlSegment && !activeTabUrlSegment) {
                    // tell subviews what is the current tab selection
                    // this is workaround for clarity tabs and routing issue
                    this.tabIdForTabViews = activeTabString;
                } else {

                    this.tabIdForTabViews = TAB_ID_NONE;

                    let navSubRoute = [];
                    if (path.length > 0) {
                        navSubRoute.push(path);
                    }
                    navSubRoute.push(this.activatedTab);

                    this.router.navigate(navSubRoute, {relativeTo: this.route});
                }
            }
        }
    }

    watchRepoClickEvent(repositoryItem) {
        this.router.navigate(['repositories', repositoryItem.name], { relativeTo: this.route });
    }

    reloadProject(project: any) {
        if (project) {
          this.entity = project;
      }
    }

    get hasProjectAdminRole(): boolean {
        return Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink,
                                [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN]);
    }

    get admiralProjectSelfLink() {
        return this.entity && this.entity.documentSelfLink;
    }

    get projectsByIdRouteRestriction() {
        return RoutesRestriction.PROJECTS_ID;
    }

    get hasAccessToRegistryReplication() {
        return Utils.isAccessAllowed(this.userSecurityContext, this.admiralProjectSelfLink,
                                                RoutesRestriction.PROJECT_REGISTRY_REPLICATION);
    }

    get embedded(): boolean {
        return FT.isApplicationEmbedded();
    }
}
