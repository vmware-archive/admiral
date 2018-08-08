/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from '../../../utils/error.service';
import { ProjectService } from '../../../utils/project.service';
import { Links } from '../../../utils/links';

import * as I18n from 'i18next';

@Component({
    selector: 'pod-details',
    templateUrl: './pod-details.component.html',
    styleUrls: ['./pod-details.component.scss']
})
/**
 * Pod details view.
 */
export class PodDetailsComponent extends BaseDetailsComponent {
    logs: any;
    loadingLogs = true;

    private logsTimeout;

    constructor(route: ActivatedRoute, router: Router, documentService: DocumentService,
                projectService: ProjectService, errorService: ErrorService) {

        super(Links.PODS, route, router, documentService, projectService, errorService);
    }

    protected entityInitialized() {
        setTimeout(() => {
            this.getLogs();

            clearInterval(this.logsTimeout);

            this.logsTimeout = setInterval(() => {
                this.getLogs();
            }, 10000);
        }, 500);
    }

    protected onProjectChange() {
        this.router.navigate(['../'], {relativeTo: this.route});
    }

    ngOnDestroy() {
        clearInterval(this.logsTimeout);
    }

    getLogs() {
        // retrieve logs for the selected pod
        this.service.getLogs(Links.POD_LOGS, this.entity.id, 10000).then(logs => {
            this.loadingLogs = false;

            this.logs = Object.keys(logs).map((key) => {
                return logs[key];
            });
        }).catch((error) =>{
            console.log('Error retrieving pod logs', error);

            this.loadingLogs = false;
            this.logs = [];
        });
    }

    get containerStatuses() {
        if (!this.entity) {
            return [];
        }

        let statuses = this.entity.pod.status.containerStatuses;

        let containerStatuses = this.entity.pod.spec.containers.map((container) => {
            let status = I18n.t('unknown');

            if (statuses) {
                let s = statuses.find((status) => status.name === container.name);
                if (s) {
                    status = Object.keys(s.state)[0];
                }
            }

            return {
                name: container.name,
                status: status
            };
        });

        return containerStatuses;
    }
}
