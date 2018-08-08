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
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseDetailsComponent } from '../../../../components/base/base-details.component';
import { DocumentService } from '../../../../utils/document.service';
import { ErrorService } from '../../../../utils/error.service';
import { ProjectService } from '../../../../utils/project.service';
import { Links } from '../../../../utils/links';
import { Utils } from '../../../../utils/utils';

import { formatUtils } from 'admiral-ui-common';

@Component({
    selector: 'app-kubernetes-cluster-edit-external',
    templateUrl: './kubernetes-cluster-edit-external.component.html',
    styleUrls: ['./kubernetes-cluster-edit-external.component.scss']
})
/**
 * Edit external kubernetes cluster view.
 */
export class KubernetesClusterEditExternalComponent extends BaseDetailsComponent {
    // action
    isUpdating: boolean;

    clusterForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl('')
    });

    constructor(protected route: ActivatedRoute, protected service: DocumentService,
                protected router: Router, protected projectService: ProjectService,
                protected errorService: ErrorService) {

        super(Links.CLUSTERS, route, router, service, projectService, errorService);
    }

    entityInitialized() {
        // Name
        this.clusterForm.get('name').setValue(this.entity.name);
        // Description
        if (this.entity.details) {
            this.clusterForm.get('description').setValue(this.entity.details);
        }
    }

    onProjectChange() {
        this.router.navigate(['../../'], {relativeTo: this.route});
    }

    update() {
        if (this.clusterForm.valid) {
            let formValues = this.clusterForm.value;

            let clusterPatch = {
                'name': formValues.name && formatUtils.escapeHtml(formValues.name),
                'details': formValues.description
            };

            this.isUpdating = true;
            this.service.patch(this.entity.documentSelfLink, clusterPatch).then(() => {
                this.isUpdating = false;

                this.goBack();
            }).catch(error => {
                console.log(error);
                this.errorService.error(Utils.getErrorMessage(error)._generic);

                this.isUpdating = false;
            });
        }
    }

    cancel() {
        this.goBack();
    }

    goBack() {
        this.router.navigate(['..'], {relativeTo: this.route});
    }
}
