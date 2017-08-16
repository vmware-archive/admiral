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

        this.loading = true;
        this.authService.findPrincipals(this.searchTerm, true).then((principalsResult) => {
            this.selectedPrincipals = principalsResult;
            this.loading = false;
        }).catch((error) => {
            console.log('Failed to find principals', error);
            this.loading = false;
        });
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

    onAssignRoles() {
        this.showAssignRolesDialog = true;
    }

    onAssignRolesDone() {
        this.showAssignRolesDialog = false;

        // refresh
        this.authService.findPrincipals(this.searchTerm, true).then((principalsResult) => {
            this.selectedPrincipals = principalsResult;
        }).catch((error) => {
            console.log('Failed to find principals', error);
        });
    }

    onAssignRolesCanceled() {
        this.showAssignRolesDialog = false;
    }

    onMakeAdmin(selectedPrincipals) {
        let roles = [];
        roles.concat(selectedPrincipals[0].roles);

        let isAdmin = roles.find((role) => {
            return role === Roles.CLOUD_ADMIN;
        });
        if (isAdmin) {
            console.log('Already a cloud admin!');
            return;
        }

        this.authService.makeCloudAdmin(selectedPrincipals[0].id).then(() => {
            // update screen
            roles.push(Roles.CLOUD_ADMIN);
            selectedPrincipals[0].roles = roles;
        }).catch((error) => {
            console.log("Failed to make cloud admin", error);
        });
    }

}
