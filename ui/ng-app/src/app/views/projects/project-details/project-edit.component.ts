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

import { Component, Input } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { ErrorService } from '../../../utils/error.service';
import { FT } from '../../../utils/ft';
import { Links } from '../../../utils/links';
import { Utils } from '../../../utils/utils';

@Component({
    selector: 'app-project-edit',
    templateUrl: './project-edit.component.html',
    styleUrls: ['./project-edit.component.scss']
})
/**
 * Update project.
 */
export class ProjectEditComponent extends BaseDetailsComponent {
    @Input() editAllowed;
    isEdit: boolean;

    isUpdatingProject: boolean = false;

    projectForm = new FormGroup({
        name: new FormControl('', Validators.required),
        description: new FormControl('', Validators.maxLength(2048)),
        icon: new FormControl(''),
        isPublic: new FormControl('')
    });

    constructor(router: Router, route: ActivatedRoute, service: DocumentService,
                protected errorService: ErrorService) {
        super(Links.PROJECTS, route, router, service, null, errorService);
    }

    get isNameInputDisabled() {
        return this.isEdit && FT.isVic() ? '' : null;
    }

    entityInitialized() {
        this.isEdit = true;

        this.projectForm.get('name').setValue(this.entity.name);

        if (this.entity.description) {
            this.projectForm.get('description').setValue(this.entity.description);
        }

        if (this.entity.isPublic) {
            this.projectForm.get('isPublic').setValue(this.entity.isPublic);
        }
    }

    goBack() {
        this.router.navigate(['..'], { relativeTo: this.route });
    }

    update() {
        if (this.projectForm.valid) {
            this.isUpdatingProject = true;

            if (this.isEdit) {
                this.service.patch(this.entity.documentSelfLink, this.projectForm.value)
                    .then(() => {
                        this.isUpdatingProject = false;
                        this.goBack();

                    }).catch((error) => {
                        this.isUpdatingProject = false;

                        console.error('Failed updating project', error);
                        this.errorService.error(Utils.getErrorMessage(error)._generic);
                    });
            } else {
                this.service.post(Links.PROJECTS, this.projectForm.value).then(() => {
                    this.isUpdatingProject = false;
                    this.goBack();

                }).catch((error) => {
                    this.isUpdatingProject = false;

                    console.error('Failed creating project', error);
                    this.errorService.error(Utils.getErrorMessage(error)._generic);

                });
            }
        }
    }
}
