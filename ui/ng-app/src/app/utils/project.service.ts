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

@Injectable()
export class ProjectService {

  private selectedProject;

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

    var iframe = document.querySelector(".former-view > iframe:first-of-type");
    if (iframe) {
      var iWindow = (<HTMLIFrameElement> iframe).contentWindow.location.reload();
    }
  }
}