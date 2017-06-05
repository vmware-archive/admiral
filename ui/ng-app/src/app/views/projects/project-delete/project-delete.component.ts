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

import { Links } from '../../../utils/links';
import { DocumentService } from '../../../utils/document.service';
import { Component, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Utils } from '../../../utils/utils';

import { BaseDetailsComponent } from '../../../components/base/base-details.component';

@Component({
  selector: 'app-project-delete',
  templateUrl: './project-delete.component.html',
  styleUrls: ['./project-delete.component.scss']
})
/**
 * Modal for deleting a project.
 */
export class ProjectDeleteComponent extends BaseDetailsComponent implements AfterViewInit {
  opened: boolean;
  alertMessage: string;

  constructor(private router: Router, route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.PROJECTS);
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.toggleModal(true);
    });
  }

  toggleModal(open) {
    this.opened = open;
    if (!open) {
      this.router.navigate(['../../'], { relativeTo: this.route });
    }
  }

  setAlert(message) {
    this.alertMessage = message;
  }

  deleteProject() {
    this.setAlert(null);
    this.service.delete(this.entity.documentSelfLink)
      .then(result => {
        this.toggleModal(false);
      })
      .catch(err => {
        this.setAlert(Utils.getErrorMessage(err)._generic);
      });

  }
}
