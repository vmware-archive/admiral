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

import { Component, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from '../../../utils/utils';

@Component({
  selector: 'app-project-create',
  templateUrl: './project-create.component.html',
  styleUrls: ['./project-create.component.scss']
})
/**
 * Modal for create/edit project.
 */
export class ProjectCreateComponent extends BaseDetailsComponent implements AfterViewInit {
  opened: boolean;
  isEdit: boolean;

  projectForm = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl('', Validators.maxLength(2048)),
      icon: new FormControl(''),
      isPublic: new FormControl('')
  });

  alertMessage: string;

  constructor(private router: Router, route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.PROJECTS);
  }

  get title() {
    return this.isEdit ? "projects.edit.titleEdit" : "projects.edit.titleNew";
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

  ngAfterViewInit() {
    this.opened = true;
  }

  toggleModal(open) {
    this.opened = open;

    if (!open) {
      let path: any[] = this.isEdit
                        ? ['../../' + Utils.getDocumentId(this.entity.documentSelfLink)]
                        : ['../'];

      this.router.navigate(path, { relativeTo: this.route });
    }
  }

  saveProject() {
    if (this.projectForm.valid) {

      if (this.isEdit) {
        this.service.patch(this.entity.documentSelfLink, this.projectForm.value).then(() => {
          this.toggleModal(false);

        }).catch((error) => {
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
      } else {

        this.service.post(Links.PROJECTS, this.projectForm.value).then(() => {
          this.toggleModal(false);

        }).catch((error) => {
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
      }
    }
  }

  resetAlert() {
    this.alertMessage = null;
  }
}
