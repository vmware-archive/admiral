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

import { Injectable } from '@angular/core';
import { DocumentService, DocumentListResult } from './document.service';
import { Ajax } from './ajax.service';
import { FT } from './ft';
import { Links } from './links';
import { URLSearchParams, RequestOptions, ResponseContentType, Headers } from '@angular/http';
import { searchConstants, serviceUtils} from 'admiral-ui-common';

@Injectable()
export class ProjectService {

  private selectedProject;

  constructor(private ds: DocumentService, private ajax: Ajax) { }

  public list() {
    if (FT.isApplicationEmbedded()) {
      return this.ajax.get(Links.GROUPS, null).then(result => {
        let documents = result || [];
        return new DocumentListResult(documents, result.nextPageLink, result.totalCount);
      });
    } else {
      return this.ds.list(Links.PROJECTS, null);
    }
  };

  public getSelectedProject() {
    if (this.selectedProject) {
      return this.selectedProject;
    }

    try {
      let localProject = JSON.parse(localStorage.getItem('selectedProject'));
      return localProject;
    } catch (e) {

    }
  }

  public setSelectedProject(project) {
    this.selectedProject = project;
    localStorage.setItem('selectedProject', JSON.stringify(project));
  }

}