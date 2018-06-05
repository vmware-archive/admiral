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
import { BaseDetailsComponent } from "../../../../components/base/base-details.component";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { Links } from "../../../../utils/links";
import { Utils}  from "../../../../utils/utils";

import * as I18n from 'i18next';


@Component({
    selector: 'app-kubernetes-cluster-new-settings',
    templateUrl: './kubernetes-cluster-new-settings.component.html'
})
/**
 * New kubernetes cluster view - settings tab.
 */
export class KubernetesClusterNewSettingsComponent extends BaseDetailsComponent
                                                    implements OnInit {
    editMode: boolean = false;

    endpoints: any[];

    isCreatingCluster: boolean = false;

    newClusterSettingsForm = new FormGroup({
        endpoint: new FormControl(''),
        name: new FormControl('', Validators.required),
        plan: new FormControl(''),
        master: new FormControl( 1, Validators.compose(
                                        [ Validators.min(1),
                                            Validators.pattern('[\\d]+'), Validators.required ])),
        worker: new FormControl(1, Validators.compose(
                                        [ Validators.min(1),
                                            Validators.pattern('[\\d]+'), Validators.required ]))
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

        super(Links.CLUSTERS, route, router, service, errorService);
    }

    ngOnInit(): void {
        super.ngOnInit();

        this.populateEndpoints();
    }

    populateEndpoints() {
        if (this.endpoints) {
            return;
        }

        this.service.list(Links.PKS_ENDPOINTS, {}).then(result => {
            this.endpoints = result.documents;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    entityInitialized() {
        if (this.entity) {
            this.editMode = true;

            this.newClusterSettingsForm.get('endpoint').disable();
            this.newClusterSettingsForm.get('name').setValue(this.entity.name);
            this.newClusterSettingsForm.get('plan').disable();
            this.newClusterSettingsForm.get('master').disable();
            // TODO finish prepopulation
        }
    }

    create() {
        // TODO Implement
        this.goBack();
    }

    update() {
        // TODO Update Cluster
        this.goBack();
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        let path = (this.editMode) ? '..' : '../clusters';

        this.router.navigate([path], {relativeTo: this.route});
    }
}
