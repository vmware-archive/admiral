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

import { Component, ViewChild } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { TableViewComponent } from '../table-view/table-view.component';
import { AuthService } from '../../utils/auth.service';
import { DocumentListResult, DocumentService } from '../../utils/document.service';
import { ProjectService } from '../../utils/project.service';
import { ErrorService } from '../../utils/error.service';
import { Constants } from '../../utils/constants';
import { FT } from '../../utils/ft';
import { Links } from '../../utils/links';
import { CancelablePromise, Utils } from '../../utils/utils';

import * as moment from 'moment';
import * as I18n from 'i18next';

@Component({
    selector: 'app-event-logs',
    templateUrl: './event-logs.component.html',
    styleUrls: ['./event-logs.component.scss'],
})
/**
 * Recent events main table view.
 */
export class EventLogsComponent {
    @ViewChild('tableView') tableView: TableViewComponent;

    eventLogs: any[] = [];
    selectedEventLogs: any[] = [];

    loadingPromise: CancelablePromise<DocumentListResult>;
    loading: boolean = false;

    isContainerDeveloper: boolean;

    showDeleteEventLogConfirmation: boolean = false;
    deleteConfirmationAlert: string;

    routerSub: any;
    idForSelection: string;
    refreshInterval: any;

    constructor(protected router: Router, protected route: ActivatedRoute,
                protected authService: AuthService, protected errorService: ErrorService,
                protected service: DocumentService, protected projectService: ProjectService) {

        projectService.activeProject.subscribe(() => {
            this.listEventLogs(true);
        });
    }

    ngOnInit() {
        this.routerSub = this.route.params.subscribe(params => {
            this.idForSelection = params.id;
        });

        this.listEventLogs(true);

        this.refreshInterval = setInterval(() => {
            this.listEventLogs(false);
        }, Constants.recentActivities.REFRESH_INTERVAL);

        if (FT.isApplicationEmbedded() && FT.isPksEnabled()) {
            this.authService.getCachedSecurityContext().then(securityContext => {
                // check if the user is only container developer
                this.isContainerDeveloper = Utils.isContainerDeveloper(securityContext);
            });
        }
    }

    ngOnDestroy() {
        this.routerSub.unsubscribe();
        clearInterval(this.refreshInterval);
    }

    refreshEventLogs($event) {
        if ($event) {
            this.listEventLogs(true);
        }
    }

    onRemove() {
        this.showDeleteEventLogConfirmation = true;
    }

    deleteConfirmed() {
        this.deleteEventLogs();
    }

    deleteCanceled() {
        this.showDeleteEventLogConfirmation = false;
    }

    isEventLogTypeInfo(event: any) {
        return event.eventLogType === Constants.recentActivities.eventLogs.INFO;
    }

    isEventLogTypeWarning(event: any) {
        return event.eventLogType === Constants.recentActivities.eventLogs.WARNING;
    }

    isEventLogTypeError(event: any) {
        return event.eventLogType === Constants.recentActivities.eventLogs.ERROR;
    }

    footerMessage() {
        return I18n.t("eventLogs.count", {
            number: this.eventLogs.length,
            interpolation: { escapeValue: false }
        } as I18n.TranslationOptions);
    }

    getTimeFromNow(eventLog) {
        if (!eventLog) {
            return null;
        }

        return this.humanizeTimeFromNow(eventLog.documentUpdateTimeMicros);
    }

    private listEventLogs(showLoadingIndicator?: boolean) {
        if (this.loadingPromise) {
            this.loadingPromise.cancel();
        }

        this.loading = showLoadingIndicator;

        this.loadingPromise = new CancelablePromise(this.service.list(Links.EVENT_LOGS, {}));

        this.loadingPromise.getPromise().then(result => {
            this.loading = false;
            this.eventLogs = result.documents;
            this.eventLogs.sort((a, b) => {
                return b.documentUpdateTimeMicros - a.documentUpdateTimeMicros;
            });

            if (this.idForSelection) {
                let eventLog = this.findEventLogById(this.idForSelection);
                this.selectEventLog(eventLog);
                this.idForSelection = null;
            }
        }).catch(error => {
            this.loading = false;
            if (error.isCanceled) {
                // ok to be canceled
            } else {
                console.error('Failed loading event logs ', error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            }
        });
    }

    private deleteEventLogs() {
        let promises: any[] = [];

        this.selectedEventLogs.forEach((eventLogsToDelete) => {
            let deletePromise = this.service.delete(eventLogsToDelete.documentSelfLink);
            promises.push(deletePromise);
        });

        Promise.all(promises).then(() => {
            this.selectedEventLogs = [];
            this.showDeleteEventLogConfirmation = false;
            this.listEventLogs(true);
        }).catch(err => {
            console.error('Failed removing event logs ', err);
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }

    private findEventLogById(id: any) {
        for (let eventLog of this.eventLogs) {
            let foundId = Utils.getDocumentId(eventLog.documentSelfLink);
            if (foundId === id) {
                return eventLog;
            }
        }

        return null;
    }

    private selectEventLog(eventLog: any) {
        if (!eventLog) {
            return;
        }

        this.selectedEventLogs.push(eventLog);
    }

    private humanizeTimeFromNow(timestampMicros) {
        var toSeconds = timestampMicros / 1000000;
        return moment.unix(toSeconds).fromNow();
    }
}
