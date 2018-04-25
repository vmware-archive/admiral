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

import { Component, Input, Output, OnChanges, EventEmitter } from '@angular/core';
import { DocumentService } from "../../../utils/document.service";
import { ErrorService } from "../../../utils/error.service";
import * as I18n from 'i18next';
import { Utils } from "../../../utils/utils";


@Component({
    selector: 'app-project-members',
    templateUrl: './project-members.component.html',
    styleUrls: ['./project-members.component.scss']
})
/**
 *  A project's members view.
 */
export class ProjectMembersComponent implements OnChanges {
    @Input() project: any;
    @Output() onChange: EventEmitter<any> = new EventEmitter();

    showAddMembers: boolean;
    showEditMember: boolean = false;
    showDeleteMember: boolean = false;

    members: any[] = [];
    selectedProjectMembers: any[] = [];

    loading: boolean = false;

    memberToDelete: any = null;
    deleteConfirmationAlert: string;
    get deleteConfirmationDescription(): string {

        return this.memberToDelete && this.memberToDelete.id
            && I18n.t('projects.members.deleteMember.confirmation',
                { projectName:  this.memberToDelete.id } as I18n.TranslationOptions);
    }

    constructor(protected service: DocumentService, protected errorService: ErrorService) {
    }

    // Add
    onAddMembers() {
        this.showAddMembers = true;
    }

    addDone() {
        this.showAddMembers = false;

        this.loadProjectAndMembers();
    }

    addCanceled() {
        this.showAddMembers = false;
    }

    // Edit
    onEdit() {
        this.showEditMember = true;
    }

    editDone() {
        this.showEditMember = false;

        this.loadProjectAndMembers();
    }

    editCanceled() {
        this.showEditMember = false;
    }

    // Delete
    onRemove() {
        this.showDeleteMember = true;
        this.memberToDelete = this.selectedProjectMembers[0];
    }

    deleteConfirmed() {
        this.deleteMember();
    }

    deleteCanceled() {
        this.memberToDelete = null;
        this.showDeleteMember = false;
    }

    ngOnChanges() {
        if (!this.project) {
            this.loadProjectAndMembers();
        } else {
            this.loadMembersOnly();
        }
    }

    private loadMembers(updatedProject: any) {
        this.members = [];

        if (!updatedProject) {
            return;
        }

        updatedProject.administrators.forEach(admin => {
            if (admin) {
                admin.role = 'ADMIN';
                this.members.push(admin);
            }
        });

        updatedProject.members.forEach(member => {
            if (member) {
                member.role = 'MEMBER';
                this.members.push(member);
            }
        });

        updatedProject.viewers.forEach(viewer => {
            if (viewer) {
                viewer.role = 'VIEWER';
                this.members.push(viewer);
            }
        });
    }

    private loadProjectAndMembers() {
        if (this.project) {

            this.loading = true;

            this.service.get(this.project.documentSelfLink, true).then((updatedProject) => {
                this.loading = false;

                this.project = updatedProject;
                this.loadMembers(this.project);
                this.onChange.emit(this.project);

            }).catch((e) => {
                this.errorService.error(Utils.getErrorMessage(e)._generic);
                this.loading = false;
            })
        }
    }

    private loadMembersOnly() {
        this.loadMembers(this.project);
    }

    private deleteMember() {
        let patchValue;

        let principalId = this.memberToDelete.id;
        let memberRole = this.getPrincipalRole(principalId);

        if (memberRole === 'ADMIN') {
            patchValue = {
                "administrators": {"remove": [principalId]}
            };
        } else if (memberRole === 'MEMBER') {
            patchValue = {
                "members": {"remove": [principalId]}
            };
        } else if (memberRole === 'VIEWER') {
            patchValue = {
                "viewers": {"remove": [principalId]}
            };
        }

        this.loading = true;
        this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
            // Successfully removed member
            this.loading = false;
            this.updateStateAfterDelete();

        }).catch((error) => {
            this.loading = false;
            if (error.status === 304) {
                // Refresh data
                this.updateStateAfterDelete();
            } else {
                console.log("Failed to remove member", error);
                this.deleteConfirmationAlert = Utils.getErrorMessage(error)._generic;
            }
        });
    }

    private getPrincipalRole(principalId) {
        let foundAdmin = this.project.administrators.find((admin) => {
            return admin.id === principalId;
        });

        if (foundAdmin) {
            return 'ADMIN';
        }

        let foundMember = this.project.members.find((member) => {
            return member.id === principalId;
        });

        if (foundMember) {
            return 'MEMBER';
        }

        let foundViewer = this.project.viewers.find((viewer) => {
            return viewer.id === principalId;
        });

        if (foundViewer) {
            return 'VIEWER'
        }

        return null;
    }

    updateStateAfterDelete() {
        this.loading = true;
        this.service.get(this.project.documentSelfLink, true).then((updatedProject) => {
            this.loading = false;
            this.memberToDelete = null;
            this.showDeleteMember = false;

            this.project = updatedProject;
            this.onChange.emit(this.project);
        }).catch((error) => {
            console.log("Failed to reload project data", error);
            this.loading = false;
            this.deleteConfirmationAlert = Utils.getErrorMessage(error)._generic;
        });
    }
}
