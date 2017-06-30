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
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { DocumentService } from "../../../utils/document.service";
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
    selector: 'app-project-add-members',
    templateUrl: './project-add-members.component.html',
    styleUrls: ['./project-add-members.component.scss']
})
/**
 * Modal add members to project.
 */
export class ProjectAddMembersComponent {

    @Input() visible: boolean;
    @Input() project: any;

    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @Output() onCancel: EventEmitter<any> = new EventEmitter();

    addMembersToProjectForm = new FormGroup({
        memberRole: new FormControl('', Validators.required)
    });
    // form data
    selectedMembers: any[] = [];
    memberRoleSelection: string = 'MEMBER';

    // data
    members: any[];
    membersSuggestions: any[];

    // error
    alertMessage: string;

    constructor(protected service: DocumentService) { }

    get description(): string {
        return I18n.t('projects.members.addMembers.description',
            { projectName:  this.project && this.project.name } as I18n.TranslationOptions);
    }

    getMembers($eventData: any) {
        if ($eventData.query === '') {
            return [];
        }

        this.service.findPrincipals($eventData.query, false).then((principalsResult) => {
            this.members = principalsResult;

            this.membersSuggestions = this.members.map((principal) => {
                let searchResult = {};
                searchResult['id'] = principal.id;
                searchResult['name'] = principal.name + ' (' + principal.id + ')'

                return searchResult;
            });
            // notify search component
            $eventData.callback(this.membersSuggestions);

        }).catch((error) => {
            console.log('Failed to find members', error);
        });
    }

    onSearchSelection(selectionData) {
        let selectedMember = this.members.find((member) => member.id === selectionData.datum.id);

        let alreadyAddedMember = this.selectedMembers.find((member) => {
            return selectedMember.id === member.id
        });

        if (!alreadyAddedMember) {
            this.selectedMembers.push(selectedMember);
        }
    }

    removeMember(selectedUser: any) {
        let idx = this.selectedMembers.findIndex((member) => member.id === selectedUser.id);
        this.selectedMembers.splice(idx, 1);
    }

    addConfirmed() {
        if (this.addMembersToProjectForm.valid) {
            let selectedPrincipalIds = this.selectedMembers.map((principal) => principal.id);

            let patchValue;
            let fieldRoleValue = this.memberRoleSelection;
            if (fieldRoleValue === 'ADMIN') {
                patchValue = {
                    "administrators": {"add": selectedPrincipalIds},
                    "members": {"remove": selectedPrincipalIds},
                    "viewers": {"remove": selectedPrincipalIds}
                };
            }

            if (fieldRoleValue === 'MEMBER') {
                patchValue = {
                    "members": {"add": selectedPrincipalIds},
                    "administrators": {"remove": selectedPrincipalIds},
                    "viewers": {"remove": selectedPrincipalIds}
                };
            }

            if (fieldRoleValue === 'VIEWER') {
                patchValue = {
                    "viewers": {"add": selectedPrincipalIds},
                    "members": {"remove": selectedPrincipalIds},
                    "administrators": {"remove": selectedPrincipalIds}
                };
            }

            this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
                this.clearState();
                this.onChange.emit(null);
            }).catch((error) => {
                console.log("Failed to add members", error);
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }

    clearState() {
        this.memberRoleSelection = 'MEMBER';
        this.members = [];
        this.membersSuggestions = [];
        this.selectedMembers = [];
        this.addMembersToProjectForm.markAsPristine();
    }

    addCanceled() {
        this.clearState();

        this.onCancel.emit(null);
    }

    resetAlert() {
        this.alertMessage = null;
    }
}
