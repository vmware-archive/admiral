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

import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { AuthService } from '../../../utils/auth.service';
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
        searchField: new FormControl(''),
        memberRole: new FormControl('', Validators.required)
    });
    // form data
    searchTerm: any;
    selectedMembers: any[] = [];
    memberRoleSelection: string = 'MEMBER';

    // data
    members: any[];
    membersSuggestions: any[];

    searching:boolean = false;
    saving:boolean = false;

    // error
    alertMessage: string;

    constructor(protected service: DocumentService, private authService: AuthService) { }

    get description(): string {
        return I18n.t('projects.members.addMembers.description',
            { projectName:  this.project && this.project.name } as I18n.TranslationOptions);
    }

    getMembers($event) {
        $event.preventDefault();
        $event.stopPropagation();

        this.searching = true;
        this.authService.findPrincipals(this.searchTerm, false).then((principalsResult) => {
            this.searching = false;
            this.members = principalsResult;

            this.membersSuggestions = this.members.map((principal) => {
                let searchResult = {};
                searchResult['id'] = principal.id;
                searchResult['name'] = principal.name + ' (' + principal.id + ')';

                return searchResult;
            });
            // Add all suggestions to the selected members, if redundant - the user will remove them.
            this.membersSuggestions.forEach(memberSuggestion => {
                let alreadyAddedMember = this.selectedMembers.find((member) => {
                    return memberSuggestion.id === member.id
                });

                if (!alreadyAddedMember) {
                    this.selectedMembers.push(memberSuggestion);
                }
            });
            // clear search input
            this.searchTerm = '';

        }).catch((error) => {
            console.log('Failed to find members', error);
            this.searching = false;

            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }

    removeMember(selectedUser: any) {
        let idx = this.selectedMembers.findIndex((member) => member.id === selectedUser.id);
        this.selectedMembers.splice(idx, 1);
    }

    addConfirmed() {
        let selectedPrincipalIds = this.getSelectedMembersToAdd();

        if (selectedPrincipalIds.length > 0) {
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

            this.saving = true;
            this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
                this.saving = false;

                this.clearState();
                this.onChange.emit(null);
            }).catch((error) => {
                console.log("Failed to add members", error);
                this.saving = false;
                this.alertMessage = Utils.getErrorMessage(error)._generic;
            });
        }
    }

    clearState() {
        this.memberRoleSelection = 'MEMBER';
        this.members = [];
        this.membersSuggestions = [];
        this.selectedMembers = [];
        // clear search
        this.searchTerm = '';
        this.resetAlert();

        this.addMembersToProjectForm.markAsPristine();
    }

    addCanceled() {
        this.clearState();
        this.resetAlert();
        this.onCancel.emit(null);
    }

    resetAlert() {
        this.alertMessage = null;
    }

    getSelectedMembersToAdd() {
        if (this.addMembersToProjectForm.valid) {
            return this.selectedMembers.map((principal) => principal.id);
        }

        return [];
    }

    disableSave() {
        return this.addMembersToProjectForm.invalid
            || this.saving
            || this.getSelectedMembersToAdd().length == 0;
    }
}
