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

import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../../components/base/base-details.component';
import { AuthService } from '../../../../utils/auth.service';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from '../../../../utils/error.service';
import { ProjectService } from '../../../../utils/project.service';
import { Links } from '../../../../utils/links';
import { Utils } from '../../../../utils/utils';

import * as I18n from 'i18next';

@Component({
    selector: 'app-kubernetes-cluster-details',
    templateUrl: './kubernetes-cluster-details.component.html',
    styleUrls: ['./kubernetes-cluster-details.component.scss']
})
/**
 * Kubernetes cluster details view.
 */
export class KubernetesClusterDetailsComponent extends BaseDetailsComponent {
    private securityContext: any;

    showDeleteConfirmation: boolean = false;
    deleteConfirmationAlert: string;

    alertType: any;
    alertMessage: string;

    constructor(route: ActivatedRoute, router: Router, authService: AuthService, service: DocumentService,
                errorService: ErrorService, projectService: ProjectService) {

        super(Links.CLUSTERS, route, router, service, projectService, errorService);

        authService.getCachedSecurityContext().then((securityContext) => {
            this.securityContext = securityContext;
        }).catch((error) => {
            console.log(error);
            this.errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    protected onProjectChange() {
        this.goBack();
    }

    deleteConfirmationTitle() {
        if (this.entity) {
            return I18n.t('kubernetes.clusters.delete.title', {
                clusterName: this.entity.name,
                interpolation: {escapeValue: false}
            } as I18n.TranslationOptions);
        }
        return '';
    }

    deleteConfirmationDescription() {
        if (this.entity) {
            return I18n.t('kubernetes.clusters.delete.confirmation', {
                clusterName: this.entity.name,
                interpolation: {escapeValue: false}
            } as I18n.TranslationOptions);
        }
        return '';
    }

    operationSupported(op) {
        return Utils.isClusterOpSupported(op, this.entity, this.securityContext);
    }

    deleteCluster($event) {
        $event.stopPropagation();

        this.showDeleteConfirmation = true;
    }

    deleteConfirmed() {
        this.service.delete(this.entity.documentSelfLink)
        .then(() => {
            this.showDeleteConfirmation = false;
            this.goBack();
        }).catch(err => {
            this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
    }

    deleteCanceled() {
        this.showDeleteConfirmation = false;
    }

    goBack() {
        this.router.navigate(['../../'], {relativeTo: this.route});
    }
}
