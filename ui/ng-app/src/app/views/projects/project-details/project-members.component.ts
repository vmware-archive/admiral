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

import { Component, Input, OnChanges } from '@angular/core';
import { DocumentService } from "../../../utils/document.service";
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

    showAddMembers: boolean;

    members: any[] = [];
    memberToDelete: any;

    selectedMember: any;

    get deleteConfirmationDescription(): string {
        return this.memberToDelete && this.memberToDelete.id
            && I18n.t('projects.members.deleteMember.confirmation',
                { projectName:  this.memberToDelete.id } as I18n.TranslationOptions);
    }
    deleteConfirmationAlert: string;

    constructor(protected service: DocumentService) { }

    onAddMembers() {
        this.showAddMembers = true;
    }

    addDone() {
        this.showAddMembers = false;
        this.loadMembers();
    }

    addCanceled() {
        this.showAddMembers = false;
    }

    onEdit(member) {
       this.selectedMember = member;
    }

    editDone() {
        this.selectedMember = null;
        this.loadMembers();
    }

    editCanceled() {
        this.selectedMember = null;
    }

    onRemove(member) {
        this.memberToDelete = member;
    }

    deleteConfirmed() {
        this.deleteMember();
    }

    deleteCanceled() {
        this.memberToDelete = null;
    }

    ngOnChanges() {
        this.loadMembers();
    }

    private loadMembers() {
        this.members = [];

        if (this.project) {

            this.service.get(this.project.documentSelfLink, true).then((updatedProject) => {

                this.project = updatedProject;

                this.project.administrators.forEach(admin => {
                    if (admin) {
                        admin.role = 'ADMIN'
                        this.members.push(admin);
                    }
                });

                this.project.members.forEach(member => {
                    if (member) {
                        member.role = 'MEMBER'
                        this.members.push(member);
                    }
                });

                this.project.viewers.forEach(viewer => {
                    if (viewer) {
                        viewer.role = 'VIEWER'
                        this.members.push(viewer);
                    }
                });

            }).catch((e) => {
                console.log('failed to update project', e);
            })
        }
    }

    private deleteMember() {
        let patchValue;

        let memberRole = this.getPrincipalRole(this.memberToDelete.id);

        if (memberRole === 'ADMIN') {
            patchValue = {
                "administrators": {"remove": [this.memberToDelete.id]}
            };
        } else if (memberRole === 'MEMBER') {
            patchValue = {
                "members": {"remove": [this.memberToDelete.id]}
            };
        } else if (memberRole === 'VIEWER') {
            patchValue = {
                "viewers": {"remove": [this.memberToDelete.id]}
            };
        }

        this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
            // Successfully removed member
            this.updateStateAfterDelete();
        }).catch((error) => {
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
        let foundMember = this.project.administrators.find((admin) => {
            return admin.id === this.memberToDelete.id;
        });

        if (foundMember) {
            return 'ADMIN';
        }

        foundMember = this.project.members.find((member) => {
            return member.id === this.memberToDelete.id;
        });

        if (foundMember) {
            return 'MEMBER';
        }

        foundMember = this.project.viewers.find((viewer) => {
            return viewer.id === this.memberToDelete.id;
        });

        if (foundMember) {
            return 'VIEWER'
        }

        return null;
    }

    updateStateAfterDelete() {
        this.service.get(this.project.documentSelfLink, true).then((updatedProject) => {
            this.memberToDelete = null;

            this.project = updatedProject;
            this.loadMembers();
        }).catch((error) => {
            console.log("Failed to reload project data", error);
            this.deleteConfirmationAlert = Utils.getErrorMessage(error)._generic;
        });
    }
}
