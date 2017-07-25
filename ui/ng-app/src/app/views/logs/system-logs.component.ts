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

import { CancelablePromise } from '../../utils/utils';
import { DocumentService, DocumentListResult } from '../../utils/document.service';
import { ErrorService } from "../../utils/error.service";
import { Links } from '../../utils/links';
import { Utils } from '../../utils/utils';

@Component({
    selector: 'system-logs',
    templateUrl: './system-logs.component.html',
    styleUrls: ['./system-logs.component.scss']
})
/**
 * Display the log events from admiral system.
 */
export class SystemLogsComponent implements OnInit {

    loading: boolean;
    loadingPromise: CancelablePromise<DocumentListResult>;

    logEntries: any[];

    constructor(protected service: DocumentService, private errorService: ErrorService) { }

    ngOnInit() {
        this.listLogEntries();
    }

    listLogEntries() {
        if (this.loadingPromise) {
            this.loadingPromise.cancel();
        }

        this.loading = true;

        this.loadingPromise = new CancelablePromise(this.service.list(Links.EVENT_LOGS, {}));

        return this.loadingPromise.getPromise().then(result => {
                this.loading = false;
                this.logEntries = result.documents;
            }).catch(error => {
                if (error) {
                    if (error.isCanceled) {
                        // ok to be canceled
                    } else {
                        console.error('Failed loading log entries ', error);
                        this.errorService.error(Utils.getErrorMessage(error)._generic);
                    }
                }
            })
    }
}
