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

import {Component, Input, OnInit} from '@angular/core';
import { Utils } from '../../../utils/utils';

@Component({
  selector: 'app-project-summary',
  templateUrl: './project-summary.component.html',
  styleUrls: ['./project-summary.component.scss']
})

/**
 *  A project's summary view.
 */
export class ProjectSummaryComponent implements OnInit {

  @Input() project: any;

  get documentId() {
    return this.project && Utils.getDocumentId(this.project.documentSelfLink);
  }

  get documentSelfLink() {
    return this.project && this.project.documentSelfLink;
  }

  get projectType() {
      return this.project
                ? (this.project.isPublic ? "projects.public" : "projects.private")
                : "unknown";
  }

  ngOnInit() {
    // DOM init
  }
}
