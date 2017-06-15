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
                let adminIds = this.project.administrators.map((admin) => admin.email);
                let devIds = this.project.members.map((member) => member.email);

                let memberIds = [];
                memberIds = memberIds.concat(adminIds);
                memberIds = memberIds.concat(devIds);
                // uniques
                memberIds = Array.from(new Set(memberIds));
                let principalCalls =
                    memberIds.map((memberId) => this.service.getPrincipalById(memberId));

                Promise.all(principalCalls).then((principalResults) => {
                    principalResults.forEach((principal) => {

                        let isAdmin = adminIds.indexOf(principal.email) > -1;
                        principal.role = isAdmin ? 'ADMIN' : 'USER';

                        this.members.push(principal);
                    });
                }).catch((e) => {
                    console.log('failed to retrieve project members', e);
                });
            }).catch((e) => {
                console.log('failed to update project', e);
            })
        }
    }

    private deleteMember() {
        let patchValue;
        if (this.memberToDelete.type === 'ADMIN') {
            patchValue = {
                "administrators": {"remove": [this.memberToDelete.id]}
            };
        }

        if (this.memberToDelete.type === 'USER') {
            patchValue = {
                "members": {"remove": [this.memberToDelete.id]}
            };
        }

        this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
            // Successfully removed member
        }).catch((error) => {
            if (error.status === 304) {
                // Refresh data
                this.service.get(this.project.documentSelfLink, true).then((updatedProject) => {
                    this.memberToDelete = null;

                    this.project = updatedProject;
                    this.loadMembers();
                }).catch((error) => {
                    console.log("Failed to reload project data", error);
                    this.deleteConfirmationAlert = Utils.getErrorMessage(error)._generic;
                });
            } else {
                console.log("Failed to remove member", error);
                this.deleteConfirmationAlert = Utils.getErrorMessage(error)._generic;
            }
        });
    }
}
