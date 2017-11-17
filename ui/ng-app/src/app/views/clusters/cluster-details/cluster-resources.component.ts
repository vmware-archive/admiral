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

import { Component, Input, ViewChild, Output, OnChanges, EventEmitter,
         OnInit, SimpleChanges, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AutoRefreshComponent } from "../../../components/base/auto-refresh.component";
import { GridViewComponent } from '../../../components/grid-view/grid-view.component';
import { DocumentService } from "../../../utils/document.service";
import { Constants } from '../../../utils/constants';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
    selector: 'app-cluster-resources',
    templateUrl: './cluster-resources.component.html',
    styleUrls: ['./cluster-resources.component.scss']
})
/**
 *  A cluster's resources view.
 */
export class ClusterResourcesComponent extends AutoRefreshComponent
                                       implements OnInit, OnChanges, AfterViewInit {
    @Input() cluster: any;
    @Input() projectLink: string;
    @Input() tabShown: boolean;
    @Output() onChange: EventEmitter<any> = new EventEmitter();
    @ViewChild('gridView') gridView: GridViewComponent;

    serviceEndpoint: string;

    showAddHost: boolean;
    showEditHost: boolean;
    hostToEdit: any;
    credentialsList: any[];
    deploymentPolicies: any[];

    hostToDelete: any;
    deleteConfirmationAlert: string;

    constructor(private service: DocumentService, protected router: Router,
                protected route: ActivatedRoute) {
        super(router, route, FT.allowHostEventsSubscription(),
                Utils.getClustersViewRefreshInterval());
    }

    ngOnInit(): void {
        this.refreshFnCallScope = this.gridView;
        this.refreshFn = this.gridView.refresh;

        super.ngOnInit();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.cluster) {
            this.serviceEndpoint = changes.cluster.currentValue.documentSelfLink + '/hosts';
        }

        if (changes.tabShown) {
            this.viewShown = changes.tabShown.currentValue;
        }
    }

    ngAfterViewInit() {
        this.service.list(Links.CREDENTIALS, {}).then(credentials => {
            this.credentialsList = credentials.documents
            .filter(c => !Utils.areSystemScopedCredentials(c));
        });
        if (FT.isApplicationEmbedded()) {
            this.service.list(Links.DEPLOYMENT_POLICIES, {}).then(policies => {
                this.deploymentPolicies = policies.documents;
            });
        }
    }

    get deleteConfirmationDescription(): string {
        return this.hostToDelete && I18n.t('hosts.actions.delete.confirmation',
            {
                hostName: this.getHostName(this.hostToDelete),
                interpolation: {escapeValue: false}
            } as I18n.TranslationOptions);
    }

    deleteHost(event, host) {
        event.stopPropagation();

        this.hostToDelete = host;
        return false; // prevents navigation
    }

    deleteConfirmed() {
        let documentSelfLinkToDelete =
            this.serviceEndpoint + '/' + Utils.getDocumentId(this.hostToDelete.documentSelfLink);

        this.service.delete(documentSelfLinkToDelete, this.projectLink).then(result => {
            this.hostToDelete = null;
            this.gridView.refresh();

        }).catch(err => {
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }

    deleteCanceled() {
        this.hostToDelete = null;
    }

    onAddHost() {
        this.showAddHost = true;
    }

    onAddHostCanceled() {
        this.showAddHost = false;
    }

    onHostEdited() {
        this.showEditHost = false;
        this.gridView.refresh();
    }

    onEditHostCanceled() {
        this.showEditHost = false;
    }

    editHost(event, host) {
        event.stopPropagation();
        this.hostToEdit = host;
        this.showEditHost = true;
        return false; // prevents navigation
    }

    onHostAdded() {
        this.showAddHost = false;
        this.gridView.refresh();
    }

    getContainersCount(host) {
        let containersCount = Utils.getCustomPropertyValue(host.customProperties, '__Containers');
        return containersCount ? Math.round(containersCount) : 0;
    }

    getDocumentId(host) {
        return Utils.getDocumentId(host.documentSelfLink);
    }

    hostState(host) {
        return I18n.t('hosts.state.' + host.powerState);
    }

    getHostName(host) {
        return Utils.getHostName(host);
    }

    getPublicAddress(host) {
        return Utils.getCustomPropertyValue(host.customProperties,
            Constants.hosts.customProperties.publicAddress);
    }

    getCpuPercentage(host, shouldRound) {
        return Utils.getCpuPercentage(host, shouldRound);
    }

    getMemoryPercentage(host, shouldRound) {
        return Utils.getMemoryPercentage(host, shouldRound);
    }

    operationSupported(op, host) {
        if (op === 'ENABLE') {
            return host.powerState === Constants.hosts.state.SUSPEND
                || host.powerState === Constants.hosts.state.OFF;
        } else if (op === 'DISABLE') {
            return host.powerState !== Constants.hosts.state.SUSPEND
                && host.powerState !== Constants.hosts.state.OFF;
        }

        return true;
    }

    enableHost(event, host) {
        event.stopPropagation();

        this.service.patch(host.documentSelfLink, {'powerState': Constants.hosts.state.ON})
        .then(result => {
            this.gridView.refresh();
            this.onChange.emit();
        })
        .catch(err => {
            console.log(Utils.getErrorMessage(err)._generic);
        });

        return false; // prevents navigation
    }

    disableHost(event, host) {
        event.stopPropagation();

        this.service.patch(host.documentSelfLink, {'powerState': Constants.hosts.state.SUSPEND})
        .then(result => {
            this.gridView.refresh();
            this.onChange.emit();
        })
        .catch(err => {
            console.log(Utils.getErrorMessage(err)._generic);
        });

        return false; // prevents navigation
    }
}
