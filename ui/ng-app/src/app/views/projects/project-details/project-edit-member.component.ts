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

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, FormControl } from "@angular/forms";

import { DocumentService } from "../../../utils/document.service";

import * as I18n from 'i18next';

@Component({
    selector: 'app-project-edit-member',
    templateUrl: './project-edit-member.component.html',
    styleUrls: ['./project-edit-member.component.scss']
})
/**
 * Modal edit member role in project.
 */
export class ProjectEditMemberComponent {

    @Input() visible: boolean;
    @Input() project: any;

    memberToEdit:any;

    @Input() get member(): any {
        return this.memberToEdit;
    }

    set member(member: any) {
        this.memberToEdit = member;
        this.memberRoleSelection = this.memberToEdit && this.memberToEdit.role;
    }

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    editMemberToProjectForm = new FormGroup({
        memberRole: new FormControl('')
    });

    memberRoleSelection: string;

    constructor(protected service: DocumentService) {
    }

    get description(): string {
        return I18n.t('projects.members.editMember.description',
          { memberId: this.memberToEdit && this.memberToEdit.id,
            projectName:  this.project && this.project.name } as I18n.TranslationOptions);
    }

    editConfirmed() {
        if (this.editMemberToProjectForm.valid) {
            let patchValue;
            let fieldRoleValue = this.memberRoleSelection;
            if (fieldRoleValue === 'ADMIN') {
                patchValue = {
                    "administrators": {"add": [this.memberToEdit.id]}
                };
            }

            if (fieldRoleValue === 'USER') {
                patchValue = {
                    "members": {"add": [this.memberToEdit.id]},
                    "administrators": {"remove": [this.memberToEdit.id]}
                };
            }

            this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
                this.onChange.emit(null);
            }).catch((error) => {
                if (error.status === 304) {
                    // actually success
                    this.onChange.emit(null);
                } else {
                    console.log("Failed to edit member", error);
                    // todo show alert message?
                }
            });
        }
    }

    editCanceled() {
        this.onCancel.emit(null);
    }
}
