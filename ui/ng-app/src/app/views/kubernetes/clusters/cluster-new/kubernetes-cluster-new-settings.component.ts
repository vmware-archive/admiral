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

import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { Links } from "../../../../utils/links";

import * as I18n from 'i18next';


@Component({
    selector: 'app-kubernetes-cluster-new-settings',
    templateUrl: './kubernetes-cluster-new-settings.component.html'
})
/**
 * New kubernetes cluster view - settings tab.
 */
export class KubernetesClusterNewSettingsComponent implements OnInit {

    endpoints: any[];

    isCreatingCluster: boolean = false;

    newClusterSettingsForm = new FormGroup({
        endpoint: new FormControl(''),
        name: new FormControl('', Validators.required),
        plan: new FormControl(''),
        master: new FormControl( 1, Validators.compose(
                                        [ Validators.min(1), Validators.required ])),
        worker: new FormControl(3, Validators.compose(
                                        [ Validators.min(3), Validators.required ]))
    });

    planSelection: any = 'SMALL';

    endpointsTitle = I18n.t('dropdownSearchMenu.title', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    endpointsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
        ns: 'base',
        entity: I18n.t('app.endpoint.entity', {ns: 'base'})
    } as I18n.TranslationOptions );

    constructor(protected route: ActivatedRoute, protected service: DocumentService,
                protected router: Router, protected errorService: ErrorService) {
    }

    ngOnInit(): void {
        this.populateEndpoints();
    }

    populateEndpoints() {
        if (this.endpoints) {
            return;
        }

        this.service.list(Links.ENDPOINTS, {}).then(result => {
            this.endpoints = result.documents;
            console.log('endpoints', this.endpoints);
        }).catch((e) => {
            console.log('Endpoints retrieval failed', e);
        });
    }

    create() {
        // TODO Implement
        this.goBack();
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }

}
