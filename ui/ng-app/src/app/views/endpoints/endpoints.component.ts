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
import { DocumentListResult, DocumentService } from "../../utils/document.service";
import { ErrorService } from "../../utils/error.service";
import { CancelablePromise, Utils} from "../../utils/utils";
import { Links } from "../../utils/links";

@Component({
    selector: 'app-endpoints',
    templateUrl: './endpoints.component.html',
    styleUrls: ['./endpoints.component.scss']
})
/**
 * Main view for Endpoints.
 */
export class EndpointsComponent implements OnInit {
    loading: boolean;
    loadingPromise: CancelablePromise<DocumentListResult>;

    endpoints: any[];
    selectedEndpoints: any[] = [];

    showDeleteConfirmation: boolean = false;
    deleteConfirmationAlert: string;

    // Creation
    constructor(protected service: DocumentService, private errorService: ErrorService) {
        //
    }

    ngOnInit() {
        this.listEndpoints({});
    }

    listEndpoints(queryOptions) {
        console.log('listEndpoints', 'queryOptions', queryOptions);

        if (this.loadingPromise) {
            this.loadingPromise.cancel();
        }

        this.loading = true;

        this.loadingPromise = new CancelablePromise(this.service.list(Links.ENDPOINTS, queryOptions));

        this.loadingPromise.getPromise().then(result => {
            this.loading = false;
            // TODO when backend is ready: this.endpoints = result.documents;
            this.endpoints = [
                {
                    documentId: 'endp1', documentSelfLink: 'endp1', name: 'Endpoint 1',
                    status: 'Active', address: 'https://endpoint1:443/'
                }, {
                    documentId: 'endp2',  documentSelfLink: 'endp2', name: 'Endpoint 2',
                    status: 'Active', address: 'https://endpoint2:443/'
                },
                {
                    documentId: 'endp3', documentSelfLink: 'endp3', name: 'Endpoint 3',
                    status: 'Active', address: 'https://endpoint3:443/'
                }, {
                    documentId: 'endp4', documentSelfLink: 'endp4', name: 'Endpoint 4',
                    status: 'Active', address: 'https://endpoint4:443/'
                }
            ];
        }).catch(error => {
            this.loading = false;
            if (error) {
                if (error.isCanceled) {
                    // ok to be canceled
                } else {
                    console.error('Failed loading endpoints ', error);
                    this.errorService.error(Utils.getErrorMessage(error)._generic);
                }
            }
        })
    }

    refreshEndpoints($event) {
        console.log('Refreshing...', $event);

        if ($event) {
            this.listEndpoints($event.queryOptions || {});
        }
    }

    removeSelectedEndpoints($event) {
        $event.stopPropagation();

        this.showDeleteConfirmation = true;
    }

    deleteConfirmed() {
        let promises: any[] = [];

        this.selectedEndpoints.forEach((endpointToDelete) => {
           let deletePromise =  this.service.delete(endpointToDelete.documentSelfLink);
           promises.push(deletePromise);
        });

        Promise.all(promises).then(() => {
            this.selectedEndpoints = [];

            this.showDeleteConfirmation = false;
        }).catch(err => {
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }

    deleteCanceled() {
        this.showDeleteConfirmation = false;
    }
}
