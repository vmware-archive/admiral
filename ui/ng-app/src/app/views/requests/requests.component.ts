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

import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { TableViewComponent } from '../table-view/table-view.component';
import { AuthService } from '../../utils/auth.service';
import { DocumentListResult, DocumentService } from '../../utils/document.service';
import { ProjectService } from '../../utils/project.service';
import { ErrorService } from '../../utils/error.service';
import { Constants } from '../../utils/constants';
import { FT } from "../../utils/ft";
import { Links } from '../../utils/links';
import { CancelablePromise, Utils } from '../../utils/utils';
import { RoutesRestriction } from '../../utils/routes-restriction';

import * as I18n from 'i18next';

@Component({
    selector: 'app-requests',
    templateUrl: './requests.component.html',
    styleUrls: ['./requests.component.scss'],
})
/**
 * Recent requests main table view.
 */
export class RequestsComponent implements OnInit, OnDestroy {
    @ViewChild('tableView') tableView: TableViewComponent;

    requests: any[] = [];
    selectedRequests: any[] = [];

    loadingPromise: CancelablePromise<DocumentListResult>;
    loading: boolean = false;
    refreshInterval: any;

    isContainerDeveloper: boolean;

    showDeleteRequestConfirmation: boolean = false;
    deleteConfirmationAlert: string;

    constructor(protected router: Router, protected route: ActivatedRoute,
                protected authService: AuthService, protected errorService: ErrorService,
                protected service: DocumentService, protected projectService: ProjectService) {

        projectService.activeProject.subscribe(() => {
            this.listRequests(true);
        });
    }

    ngOnInit() {
        this.listRequests(true);

        this.refreshInterval = setInterval(() => {
            this.listRequests(false);
        }, Constants.recentActivities.REFRESH_INTERVAL);

        if (FT.isApplicationEmbedded() && FT.isPksEnabled()) {
            this.authService.getCachedSecurityContext().then(securityContext => {
                // check if the user is only container developer
                this.isContainerDeveloper = Utils.isContainerDeveloper(securityContext);
            });
        }
    }

    ngOnDestroy() {
        clearInterval(this.refreshInterval);
    }

    refreshRequests($event) {
        if ($event) {
            this.listRequests(true);
        }
    }

    onRemove() {
        this.showDeleteRequestConfirmation = true;
    }

    deleteConfirmed() {
        this.deleteRequests();
    }

    deleteCanceled() {
        this.showDeleteRequestConfirmation = false;
    }

    hasResourceIds(document: any) {
        return this.resourceIds(document) && this.resourceIds.length > 0;
    }

    isFailed(request: any) {
        return request.taskInfo.stage === Constants.recentActivities.requests.FAILED
            || request.taskInfo.stage === Constants.recentActivities.requests.CANCELLED;
    }

    isRunning(request: any) {
        return request.taskInfo.stage === Constants.recentActivities.requests.CREATED
            || request.taskInfo.stage === Constants.recentActivities.requests.STARTED;
    }

    isSuccessfull(request: any) {
        return !this.isRunning(request) && !this.isFailed(request);
    }

    getEventLogId(request: any) {
        return Utils.getDocumentId(request.eventLogLink);
    }

    footerMessage() {
        return I18n.t("requests.count", {
            number: this.requests.length,
            interpolation: { escapeValue: false }
        } as I18n.TranslationOptions);
    }

    getDocumentId(request: any) {
        if(!request || !request.resourceLinks) {
            return;
        }
        //redirects to the last one in order to be consistent with the old ui
        return Utils.getDocumentId(request.resourceLinks[request.resourceLinks.length - 1]);
    }

    deploymentsRouteRestriction(){
        return RoutesRestriction.DEPLOYMENTS;
    }

    isContainer(request: any) {
        if (!request || !request.resourceLinks) {
            return false;
        }

        return request.resourceLinks.some(resourceLink => resourceLink.indexOf(Links.CONTAINERS) !== -1);
    }

    isCompositeComponent(request: any) {
        if (!request || !request.resourceLinks) {
            return false;
        }

        return request.resourceLinks.some(resourceLink => resourceLink.indexOf(Links.COMPOSITE_COMPONENTS) !== -1);
    }

    isContainerNetwork(request: any) {
        if (!request || !request.resourceLinks) {
            return false;
        }

        return request.resourceLinks.some(resourceLink => resourceLink.indexOf(Links.CONTAINER_NETWORKS) !== -1);
    }

    isContainerVolume(request: any) {
        if (!request || !request.resourceLinks) {
            return false;
        }

        return request.resourceLinks.some(resourceLink => resourceLink.indexOf(Links.CONTAINER_VOLUMES) !== -1);
    }

    canNavigateTo(direction: string, request: any) {
        if (request.eventLogLink || this.isRunning(request)) {
            return false;
        }

        switch (direction) {
            case Constants.recentActivities.requests.navigation.container:
                return this.isContainer(request);
            case Constants.recentActivities.requests.navigation.compositeComponent:
                return this.isCompositeComponent(request);
            case Constants.recentActivities.requests.navigation.network:
                return this.isContainerNetwork(request);
            case Constants.recentActivities.requests.navigation.volume:
                return this.isContainerVolume(request);
            default:
                return false;
        }
    }

    deploymentsRouterLink(deploymentsSubTab: string, request: string) {
        let documentId = this.getDocumentId(request);
        return `../${deploymentsSubTab}?$occurrence=any&documentId=${documentId}`;
    }

    private listRequests(showLoadingIndicator?: boolean) {
        if (this.loadingPromise) {
            this.loadingPromise.cancel();
        }

        this.loading = showLoadingIndicator;

        this.loadingPromise = new CancelablePromise(
                                        this.service.list(Links.REQUEST_STATUS, {}));

        this.loadingPromise.getPromise().then(result => {
            this.loading = false;
            this.requests = result.documents;
            this.requests.sort((a, b) => {
                return b.documentUpdateTimeMicros - a.documentUpdateTimeMicros;
            });

        }).catch(error => {
            this.loading = false;
            if (error.isCanceled) {
                // ok to be canceled
            } else {
                console.error('Failed loading requests ', error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            }
        });
    }

    private requestTitleText(document: any) {
        if (this.hasResourceIds(document)) {
            return this.resourceIds(document);
        } else {
            return document.name;
        }
    }

    private resourceIds(document: any) {
        return document.resourceLinks && document.resourceLinks.map((resourceLink) => {
            return Utils.getDocumentId(resourceLink);
        });
    }

    private deleteRequests() {
        let promises: any[] = [];

        this.selectedRequests.forEach((requestsToDelete) => {
            let deletePromise = this.service.delete(requestsToDelete.documentSelfLink);
            promises.push(deletePromise);
        });

        Promise.all(promises).then(() => {
            this.selectedRequests = [];
            this.showDeleteRequestConfirmation = false;
            this.listRequests(true);
        }).catch(err => {
            console.error('Failed removing requests ', err);
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }
}
