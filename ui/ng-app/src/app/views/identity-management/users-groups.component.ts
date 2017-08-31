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

import { Component } from '@angular/core';
import { FormGroup, FormControl } from "@angular/forms";
import { AuthService } from '../../utils/auth.service';
import { Roles } from '../../utils/roles';

@Component({
    selector: 'app-identity-usersgroups',
    templateUrl: './users-groups.component.html',
    styleUrls: ['./users-groups.component.scss']
})
/**
 * Tab displaying the users and groups in the system.
 */
export class UsersGroupsComponent {

    searchPrincipalsForm = new FormGroup({
        searchField: new FormControl('')
    });

    searchTerm: string = '';
    loading: boolean = false;

    selected: any[] = [];
    selectedPrincipals: any[] = [];

    showAssignRolesDialog: boolean = false;

    constructor(protected authService: AuthService) {
    }

    searchIt($event) {
        this.searchTerm = this.searchPrincipalsForm.get("searchField").value;

        if (this.searchTerm === '') {
            this.selectedPrincipals = [];
            return;
        }

        this.loadPrincipals();
    }

    onAssignRoles() {
        this.showAssignRolesDialog = true;
    }

    onAssignRolesDone() {
        this.showAssignRolesDialog = false;
        // refresh
        this.loadPrincipals();
    }

    onAssignRolesCanceled() {
        this.showAssignRolesDialog = false;
    }

    onMakeCloudAdmin(selectedPrincipal) {
        this.authService.assignRoleCloudAdmin(selectedPrincipal.id).then(() => {
            // update screen
            selectedPrincipal.isCloudAdmin = true;
        }).catch((error) => {
            console.log("Failed to make cloud admin", error);
        });
    }

    onUnmakeCloudAdmin(selectedPrincipal) {
        this.authService.unassignRoleCloudAdmin(selectedPrincipal.id).then(() => {
            // update screen
            selectedPrincipal.isCloudAdmin = false;
        }).catch((error) => {
            console.log("Failed to unmake cloud admin", error);
        });
    }

    loadPrincipals() {
        this.loading = true;

        this.authService.findPrincipals(this.searchTerm, true)
        .then((principalsResult) => {
            this.loading = false;
            this.formatResult(principalsResult);

        }).catch((error) => {
            console.log('Failed to find principals', error);
            this.loading = false;
        });
    }

    formatResult(principalsResult) {
        if (!principalsResult) {
            this.selectedPrincipals = [];
            return;
        }

        let principals: any[] = [];
        principalsResult.forEach((selectedPrincipal) => {
            let principal = {
                id: selectedPrincipal.id,
                name: selectedPrincipal.name,
                type: selectedPrincipal.type,
                isCloudAdmin: this.isCloudAdmin(selectedPrincipal),
                projectRoles: this.getProjectsRoles(selectedPrincipal),
                projects: selectedPrincipal.projects
            };
            principals.push(principal);
        });

        this.selectedPrincipals = principals;
    }

    isCloudAdmin(principal: any) {
        let roles: any[] = principal.roles;
        if (!roles) {
            return false;
        }

        let cloudAdminRole: any = roles.find((role) => {
            return role === Roles.CLOUD_ADMIN;
        });

        return !!cloudAdminRole;
    }

    getProjectsRoles(principal: any) {
        let projectsRoles: any[] = [];

        let projects = principal.projects;
        if (projects) {
            projects.forEach((project) => {
                if (project.roles && project.roles.length > 0) {
                    // we are not showing PROJECT_MEMBER_EXTENDED
                    let idxRole = project.roles.indexOf(Roles.PROJECT_MEMBER);
                    if (idxRole === -1) {
                        idxRole = 0;
                    }
                    projectsRoles.push({
                        projectName: project.name,
                        projectRole: project.roles[idxRole]
                    });
                }
            });
        }

        return projectsRoles;
    }
}
