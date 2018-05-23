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

import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { DocumentService } from "../../../../utils/document.service";
import { ErrorService } from "../../../../utils/error.service";
import { Links } from "../../../../utils/links";
import { Utils } from "../../../../utils/utils";

import * as I18n from 'i18next';

@Component({
    selector: 'app-kubernetes-cluster-add-existing',
    templateUrl: './kubernetes-cluster-add-existing.component.html'
})
/**
 * View for adding existing clusters.
 */
export class KubernetesClusterAddExistingComponent implements OnInit {
    loading: boolean = false;
    isAdding: boolean = false;
    endpoints: any[];

    clusters: any[] = [];
    selectedClusters: any[];

    addExistingClustersForm = new FormGroup({
        endpoint: new FormControl('')
    });

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
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    onChangeEndpoint($event) {
        console.log('Endpoint selection changed', $event);

        // TODO Retrieve available clusters from the selected endpoint
    }

    add() {
        // TODO Add the selected clusters to the system

        this.goBack();
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['../clusters'], {relativeTo: this.route});
    }
}
