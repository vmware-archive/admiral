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

import { Component, OnInit } from '@angular/core';
import { FormGroup } from "@angular/forms";
import { DocumentService } from "../../utils/document.service";

@Component({
    selector: 'app-identity-usersgroups',
    templateUrl: './users-groups.component.html',
    styleUrls: ['./users-groups.component.scss']
})
/**
 * Tab displaying the users and groups in the system.
 */
export class UsersGroupsComponent implements OnInit {

    searchPrincipalsForm = new FormGroup({
    });

    principals: any[] = [];
    selectedPrincipals: any[] = [];

    principalSuggestions: any[];

    constructor(protected service: DocumentService) {
    }

    ngOnInit() {
    }

    searchPrincipals($eventData: any) {
        if ($eventData.query === '') {
            this.principals = [];
            return;
        }

        this.service.findPrincipals($eventData.query).then((principalsResult) => {
            this.principals = principalsResult;

            this.principalSuggestions = this.principals.map((principal) => {
                let searchResult = {};
                searchResult['id'] = principal.id;
                searchResult['name'] = principal.email;

                return searchResult;
            });
            // notify search component
            $eventData.callback(this.principalSuggestions);

        }).catch((error) => {
            console.log('Failed to find principals', error);
        });
    }

    onSearchSelection(selectionData) {
        let selectedPrincipal = this.principals.find((principal) => principal.id === selectionData.datum.id);
        this.selectedPrincipals = [];
        this.selectedPrincipals.push(selectedPrincipal);
    }

    clearSearch() {
        this.principals = [];
        this.selectedPrincipals = [];
    }
}
