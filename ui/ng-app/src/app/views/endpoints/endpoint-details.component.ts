/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { FormControl, FormGroup } from "@angular/forms";
import { BaseDetailsComponent } from '../../components/base/base-details.component';
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Links } from "../../utils/links";


@Component({
    selector: 'app-endpoint-details',
    templateUrl: './endpoint-details.component.html'
})
/**
 * View for endpoint creation.
 */
export class EndpointDetailsComponent extends BaseDetailsComponent {
    editMode: boolean = false;

    endpointDetailsForm = new FormGroup({
        name: new FormControl(''),
        description: new FormControl(''),
        address: new FormControl(''),
        userName: new FormControl(''),
        password: new FormControl('')
    });

    isSavingEndpoint: boolean = false;
    isTestingConnection: boolean = false;

    alertMessage: string;

    constructor(route: ActivatedRoute, documentService: DocumentService, router: Router,
                errorService: ErrorService) {
        super(Links.ENDPOINTS, route, router, documentService, errorService);
    }

    protected entityInitialized() {
        if (this.entity) {
            // edit mode
            this.editMode = true;
        }
    }

    create() {

    }

    save() {

    }

    testConnection() {

    }

    resetAlert() {
        this.alertMessage = null;
    }
}
