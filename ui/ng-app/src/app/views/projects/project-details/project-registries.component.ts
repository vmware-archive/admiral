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

import { ActivatedRoute } from '@angular/router';
import { Component, Input, OnInit } from '@angular/core';
import { DocumentListResult, DocumentService } from "../../../utils/document.service";
import { ErrorService } from "../../../utils/error.service";
import { CancelablePromise, Utils} from "../../../utils/utils";
import { Links } from "../../../utils/links";
import * as I18n from 'i18next';

@Component({
    selector: 'app-project-registries',
    templateUrl: './project-registries.component.html',
    styleUrls: ['./project-registries.component.scss']
})
/**
 * View for Project Registries.
 */
export class ProjectRegistriesComponent implements OnInit {
    @Input() tabId: string = '';

    loading: boolean;
    loadingPromise: CancelablePromise<DocumentListResult>;

    projectRegistries: any[];
    selectedProjectRegistries: any[] = [];

    showDeleteConfirmation: boolean = false;
    deleteConfirmationAlert: string;
    projectLink: string;

    private sub: any;

    // alert display
    tabAlertClosed: boolean = true;
    tabAlertType: string = 'alert-warning';
    tabAlertText: string;

    constructor(protected service: DocumentService, private errorService: ErrorService,
                private route: ActivatedRoute) {
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            let projectId = params['projectId'] || params['id'];
            if (projectId) {
                this.projectLink = Links.PROJECTS + '/' + projectId;
            }
        });

        this.listProjectRegistries({});
    }

    ngOnDestroy() {
        this.sub.unsubscribe();
    }

    listProjectRegistries(queryOptions) {
        if (this.loadingPromise) {
            this.loadingPromise.cancel();
        }

        this.loading = true;

        this.loadingPromise = new CancelablePromise(
                            this.service.list(Links.REGISTRIES, queryOptions, this.projectLink));

        this.loadingPromise.getPromise().then(result => {
            this.loading = false;
            this.projectRegistries = result.documents;
        }).catch(error => {
            this.loading = false;
            if (error) {
                if (error.isCanceled) {
                    // ok to be canceled
                } else {
                    console.error('Failed loading registries ', error);
                    this.errorService.error(Utils.getErrorMessage(error)._generic);
                }
            }
        })
    }

    refreshProjectRegistries($event) {
        if ($event) {
            this.listProjectRegistries($event.queryOptions || {});
        }
    }

    removeSelectedRegistries($event) {
        $event.stopPropagation();

        this.showDeleteConfirmation = true;
    }

    deleteConfirmed() {
        let promises: any[] = [];
        let globalRegistriesSelected: boolean = false;

        this.selectedProjectRegistries.forEach((registriesToDelete) => {
            if (this.isProjectSpecificRegistry(registriesToDelete)) {
                let deletePromise =  this.service.delete(registriesToDelete.documentSelfLink);
                promises.push(deletePromise);
            } else {
                globalRegistriesSelected = true;
            }
        });

        Promise.all(promises).then(() => {
            this.selectedProjectRegistries = [];
            this.showDeleteConfirmation = false;
            this.listProjectRegistries({});
            if (globalRegistriesSelected) {
                this.warnGlobalRegistriesSkippedDeletion();
            }
        }).catch(err => {
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }

    deleteCanceled() {
        this.showDeleteConfirmation = false;
    }

    private warnGlobalRegistriesSkippedDeletion(): void {
        this.tabAlertType = 'alert-warning';
        this.tabAlertText =
            I18n.t('projects.projectRegistries.globalRegistriesDeletionSkipped.warningMessage');
        this.tabAlertClosed = false;
    }

    resetAlert() {
        this.tabAlertType = null;
        this.tabAlertText = null;
    }

    getRegistryId(registry){
        return Utils.getDocumentId(registry.documentSelfLink);
    }

    isProjectSpecificRegistry(registry): boolean {
        let hasRegistryTenantLinks = registry && registry.tenantLinks
                                        && Array.isArray(registry.tenantLinks);

        return hasRegistryTenantLinks && this.projectLink
                    && registry.tenantLinks.includes(this.projectLink);
    }

    getEditLink(registry: any) {
        return './'+ this.tabId + '/registry/' + this.getRegistryId(registry) + '/edit';
    }
}
