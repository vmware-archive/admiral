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
import {FormControl, FormGroup, Validators} from "@angular/forms";
import { BaseDetailsComponent } from '../../components/base/base-details.component';
import { DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { Constants } from "../../utils/constants";
import { Links } from "../../utils/links";
import { Utils } from "../../utils/utils";

import * as I18n from 'i18next';


@Component({
    selector: 'app-endpoint-details',
    templateUrl: './endpoint-details.component.html',
    styleUrls: ['./endpoint-details.component.scss']
})
/**
 * View for endpoint creation.
 */
export class EndpointDetailsComponent extends BaseDetailsComponent {
    editMode: boolean = false;

    credentials: any[];

    endpointDetailsForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl(''),
        uaaAddress: new FormControl('', Validators.required),
        uaaCredentials: new FormControl('', Validators.required),
        pksAddress: new FormControl('', Validators.required)
    });

    isSavingEndpoint: boolean = false;
    isTestingConnection: boolean = false;

    alertType: any;
    alertMessage: string;

    credentialsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.credential.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(route: ActivatedRoute, documentService: DocumentService, router: Router,
                errorService: ErrorService) {
        super(Links.ENDPOINTS, route, router, documentService, errorService);
    }

    protected entityInitialized() {
        if (this.entity) {
            // edit mode
            this.editMode = true;
            // Name
            this.endpointDetailsForm.get('name').setValue(this.entity.name);
            // Description
            if (this.entity.details) {
                this.endpointDetailsForm.get('description').setValue(this.entity.details);
            }

            // TODO uaa and pks address population

            // TODO credentials population
            let authCredentialsLink =
                Utils.getCustomPropertyValue(this.entity.customProperties, '__authCredentialsLink');
            if (authCredentialsLink) {
                let credItem = this.credentials
                                    .filter((c) => c.documentSelfLink === authCredentialsLink);
                if (credItem.length > 0) {
                    this.endpointDetailsForm.get('uaaCredentials').setValue(credItem[0]);
                }
            }
        }
    }

    ngOnInit() {
        super.ngOnInit();

        this.populateCredentials();
    }

    populateCredentials() {
        if (this.credentials) {
            return;
        }

        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentials = credentials.documents
                                    .filter(c => !Utils.areSystemScopedCredentials(c))
                                    .map(Utils.toCredentialViewModel);
        }).catch((e) => {
            console.log('Credentials retrieval failed', e);
        });
    }

    create() {
        console.log('create');

        if (this.endpointDetailsForm.valid) {
            // TODO - if success -> this.goBack(); else show err, stay on same screen
            this.isSavingEndpoint = true;

        }
    }

    save() {
        console.log('save');

        if (this.endpointDetailsForm.valid) {
            // TODO - if success -> this.goBack(); else show err, stay on same screen
            // this.isSavingEndpoint = true;

        }
    }

    testConnection() {
        console.log('test connection');

        if (this.endpointDetailsForm.valid) {
            // TODO - if success show green message - connection success, else show red err message
            // this.isTestingConnection = true;
            //
            // this.alertType = Constants.alert.type.SUCCESS;
            // this.alertMessage = I18n.t('endpoint.connectionVerified');

        }
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../'], {relativeTo: this.route});
    }

    resetAlert() {
        this.alertType = null;
        this.alertMessage = null;
    }
}
