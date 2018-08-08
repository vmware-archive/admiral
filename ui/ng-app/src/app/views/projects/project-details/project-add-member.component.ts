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

import { Component, AfterViewInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Router, ActivatedRoute } from '@angular/router';
import { BaseDetailsComponent } from "../../../components/base/base-details.component";
import { ErrorService } from "../../../utils/error.service";
import { DocumentService } from '../../../utils/document.service';
import { Links } from '../../../utils/links';

@Component({
    selector: 'app-project-add-member',
    templateUrl: './project-add-member.component.html',
    styleUrls: ['./project-add-member.component.scss']
})
/**
 * Add Members to Project dialog.
 */
export class ProjectAddMemberComponent extends BaseDetailsComponent implements AfterViewInit {

    opened: boolean;

    memberIdInput: string = '';
    memberRoleSelection: string = 'USER';

    addMemberToProjectForm = new FormGroup({
        memberId: new FormControl('', Validators.required),
        memberRole: new FormControl('')
    });

    constructor(router: Router, route: ActivatedRoute, service: DocumentService,
                errorService: ErrorService) {
        super(Links.PROJECTS, route, router, service, null, errorService);
    }

    ngAfterViewInit() {
        setTimeout(() => {
            this.opened = true;
        });
    }

    toggleModal(open) {
        this.opened = open;
        if (!open) {
            let path: any[] =  ['../'];
            this.router.navigate(path, { relativeTo: this.route });
        }
    }

    entityInitialized() {
    }

    addMember() {
        if (this.addMemberToProjectForm.valid) {
            let patchValue;
            let fieldRoleValue = this.memberRoleSelection;
            if (fieldRoleValue === 'ADMIN') {
                patchValue = {
                    "administrators": {"add": [this.memberIdInput]}
                };
            }

            if (fieldRoleValue === 'USER') {
                patchValue = {
                    "members": {"add": [this.memberIdInput]}
                };
            }

            this.service.patch(this.entity.documentSelfLink, patchValue).then(() => {
                this.toggleModal(false);
            }).catch((error) => {
                if (error.status === 304) {
                    // actually success
                    this.toggleModal(false);
                } else {
                    console.log("Failed to add member", error);
                }
            });
        }
    }
}
