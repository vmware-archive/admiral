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

import {Component, Input, OnChanges} from '@angular/core';
import {DocumentService} from "../../../utils/document.service";


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

    members: any[] = [];

    constructor(protected service: DocumentService) { }

    onEdit(member) {
        // TODO Implementation
    }

    onDelete(member) {
        let patchValue;
        if (member.type === 'ADMIN') {
            patchValue = {
                "administrators": {"remove": [member.id]}
            };
        }

        if (member.type === 'USER') {
            patchValue = {
                "members": {"remove": [member.id]}
            };
        }

        this.service.patch(this.project.documentSelfLink, patchValue).then(() => {
            console.log("Successfully to removed member");
        }).catch((error) => {
            if (error.status === 304) {
                // actually success
                // TODO refresh screen
            } else {
                console.log("Failed to remove member", error);
            }
        });
    }

    ngOnChanges() {
        this.members = [];

        if (this.project) {
            // Load members details
            let memberIds = [];
            memberIds = memberIds.concat(this.project.administrators.map((admin) => admin.email));
            memberIds = memberIds.concat(this.project.members.map((member) => member.email));
            // uniques
            memberIds = Array.from(new Set(memberIds));
            let principalCalls =
                            memberIds.map((memberId) => this.service.getPrincipalById(memberId));

            Promise.all(principalCalls).then((principalResults) => {
                principalResults.forEach((principal) => {
                    let isAdmin =
                        this.project.administrators.filter((admin) => admin.id === principal.id);
                    principal.role = isAdmin ? 'ADMIN' : 'USER';

                    this.members.push(principal);
                });
            }).catch((e) => {
                console.log('failed to retrieve project members', e);
            });
        }
    }
}
