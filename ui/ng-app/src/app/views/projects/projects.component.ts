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

import { Component, ViewChild } from '@angular/core';
import { Links } from '../../utils/links';
import { DocumentService } from '../../utils/document.service';
import { GridViewComponent } from '../../components/grid-view/grid-view.component';
import { Utils } from '../../utils/utils';
import * as I18n from 'i18next';

@Component({
  selector: 'app-projects',
  templateUrl: './projects.component.html',
  styleUrls: ['./projects.component.scss']
})
/**
 * Projects main component.
 */
export class ProjectsComponent {

  constructor(private service: DocumentService) { }

  serviceEndpoint = Links.PROJECTS;
  projectToDelete: any;
  deleteConfirmationAlert: string;

  @ViewChild('gridView') gridView:GridViewComponent;

  get deleteConfirmationTitle(): string {
    return this.projectToDelete && this.projectToDelete.name;
  }

  get deleteConfirmationDescription(): string {
    return this.projectToDelete && this.projectToDelete.name
            && I18n.t('projects.delete.confirmation',
            { projectName:  this.projectToDelete.name } as I18n.TranslationOptions);
  }

  deleteProject(event, project) {
    this.projectToDelete = project;
    event.stopPropagation();
    return false; // prevents navigation
  }

  deleteConfirmed() {
    this.service.delete(this.projectToDelete.documentSelfLink)
        .then(result => {
          this.projectToDelete = null;
          this.gridView.refresh();
        })
        .catch(err => {
          this.deleteConfirmationAlert = Utils.getErrorMessage(err)._generic;
        });
  }

  deleteCanceled() {
    this.projectToDelete = null;
  }
}
