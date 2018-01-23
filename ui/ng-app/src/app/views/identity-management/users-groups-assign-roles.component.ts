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

import { Component, OnChanges, OnInit, EventEmitter, Input, Output, SimpleChanges } from '@angular/core';
import { FormGroup, FormArray, FormBuilder } from "@angular/forms";
import { DocumentService } from "../../utils/document.service";
import { Links } from "../../utils/links";
import { Roles } from "../../utils/roles";
import { Utils } from "../../utils/utils";
import * as I18n from 'i18next';

@Component({
    selector: 'app-users-assign-roles',
    templateUrl: './users-groups-assign-roles.component.html',
    styleUrls: ['./users-groups-assign-roles.component.scss']
})
/**
 * Assign roles in projects to a selected user/group.
 */
export class UsersGroupsAssignRolesComponent implements OnInit, OnChanges {

    @Input() visible: boolean;
    @Input() principal: any;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    projects: any[];

    formBuilder: FormBuilder = new FormBuilder();
    formAssignToProjects: FormGroup;

    removedAssigments: any[] = [];

    alertMessage: string;

    constructor(protected service: DocumentService) {
    }

    get description(): string {
        return I18n.t('identity.usersgroups.assignRoleInProject.description',
            { principal:  this.principal && this.principal.name } as I18n.TranslationOptions);
    }

    get assignments(): FormArray {
        return this.formAssignToProjects
            && this.formAssignToProjects.get('assignments') as FormArray;
    }

    ngOnInit() {
        this.formAssignToProjects = this.formBuilder.group({
            assignments: this.formBuilder.array([])
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.principal) {
            this.generateAssignmentRows();
        }
    }

    generateAssignmentRows() {
        // Clear assignments
        this.clearAssignments();

        // already existing
        if (this.principal && this.principal.projects) {
            this.principal.projects.forEach((projectAssignment) => {
                // we are not showing PROJECT_MEMBER_EXTENDED
                let idxRole = projectAssignment.roles.indexOf(Roles.PROJECT_MEMBER);
                if (idxRole === -1) {
                    idxRole = 0;
                }
                this.addAssignment(projectAssignment.name, projectAssignment.roles[idxRole], true);
            });
        }

        // new one
        this.service.list(Links.PROJECTS, null).then((result) => {
            this.projects = result.documents;
            this.addAssignment('', '', false);
        }).catch((error) => {
            console.log(error);
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    createAssignment(projectValue, roleValue, existing) {
        return this.formBuilder.group({
            project: projectValue,
            role: roleValue,
            existing: existing
        });
    }

    addAssignment(projectValue, roleValue, existing): void {
        this.assignments.push(this.createAssignment(projectValue, roleValue, existing));
    }

    addEmptyAssignment(event): void {
        event.preventDefault();

        this.addAssignment('', '', false);
    }

    clearAssignments(): void {
        if (this.formAssignToProjects && this.assignments) {
            let temp = this.assignments;
            let tempCount = this.assignments.length;
            for (let i = 0; i < tempCount; i++) {
                temp.removeAt(0);
            }
        }

        this.removedAssigments = [];
    }

    removeAssignment(event, index): void {
        event.preventDefault();

        let control = this.assignments.at(index);
        let controlValue = control.value;

        this.removedAssigments.push({
            projectName: controlValue.project,
            role: controlValue.role
        });

        this.assignments.removeAt(index);

        if (this.assignments.length === 0) {
            // all assignments have been removed - add an empty one
            this.addAssignment('', '', false);
        }
    }

    assignmentsConfirmed() {
        this.updateAssignments();

        this.removeAssignments();
    }

    updateAssignments() {
        this.assignments.value.forEach((value) => {
            let projectName = value.project;
            let role = value.role;

            let theProject = this.projects.find((project) => {
                return project.name === projectName;
            });

            if (!theProject) {
                console.log('The project is not defined! Cannot update assignments!', projectName);
                return;
            }

            let removalActionIdx = this.removedAssigments.findIndex((entry) => {
                return entry.projectName === projectName;
            });

            if (removalActionIdx > -1) {
                // cancel removal action
                let removalAction = this.removedAssigments[removalActionIdx];
                this.removedAssigments.splice(removalActionIdx, 1);

                if (removalAction.role === role) {
                    // do nothing
                } else {
                    this.updateProjectAssignments(theProject, role);
                }
            } else {
                this.updateProjectAssignments(theProject, role);
            }
        });
    }

    private updateProjectAssignments(theProject: any, role: any) {
        let patchValue;

        if (role === 'PROJECT_ADMIN') {
            patchValue = {
                "administrators": {"add": [this.principal.id]},
                "members": {"remove": [this.principal.id]},
                "viewers": {"remove": [this.principal.id]}
            };
        }

        if (role === 'PROJECT_MEMBER') {
            patchValue = {
                "members": {"add": [this.principal.id]},
                "administrators": {"remove": [this.principal.id]},
                "viewers": {"remove": [this.principal.id]}
            };
        }

        if (role === 'PROJECT_VIEWER') {
            patchValue = {
                "viewers": {"add": [this.principal.id]},
                "members": {"remove": [this.principal.id]},
                "administrators": {"remove": [this.principal.id]}
            };
        }

        if (patchValue) {
            this.updateProject(theProject, patchValue);
        } else {
            console.log('cannot update assignments for project ', theProject.name,
                'unsupported role ', role);
        }
    }

    removeAssignments() {
        this.removedAssigments.forEach((entry) => {
            let projectName = entry.projectName;

            let theProject = this.projects.find((project) => {
                return project.name === projectName;
            });

            if (!theProject) {
                console.log('The project is not defined! Cannot remove assignments!', projectName);
                return;
            }

            let patchValue = {
                "members": {"remove": [this.principal.id]},
                "administrators": {"remove": [this.principal.id]},
                "viewers": {"remove": [this.principal.id]}
            }; // assuming not more than one role per project

            this.updateProject(theProject, patchValue);
        });
    }

    updateProject(project, patchValue) {
        this.service.patch(project.documentSelfLink, patchValue).then((value) => {
            // update project's roles
            project.roles = value.roles;

            this.onChange.emit(null);
            this.clearState();
        }).catch((error) => {
            console.log("Failed to update project roles", error);
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    assignmentsCanceled() {
        this.clearState();

        this.onCancel.emit(null);
    }

    clearState() {
        this.resetAlert();

        this.generateAssignmentRows();

        this.formAssignToProjects.markAsPristine();
    }

    resetAlert() {
        this.alertMessage = null;
    }
}
