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

import { Component, Input, OnChanges } from '@angular/core';
import { FormGroup, FormControl } from "@angular/forms";
import { DocumentService } from './../../../utils/document.service';

@Component({
  selector: 'app-project-configuration',
  templateUrl: './project-configuration.component.html',
  styleUrls: ['./project-configuration.component.scss']
})
export class ProjectConfigurationComponent implements OnChanges {

  @Input() project: any;
  publicRegistry;
  enableContentTrust;
  automaticallyScanImagesOnPush;
  preventVulnerableImagesFromRunning;
  preventVulnerableImagesFromRunningSeverity;

  configForm = new FormGroup({
      publicRegistry: new FormControl(''),
      enableContentTrust: new FormControl(''),
      automaticallyScanImagesOnPush: new FormControl(''),
      preventVulnerableImagesFromRunning: new FormControl(''),
      preventVulnerableImagesFromRunningSeverity: new FormControl('')
  });

  constructor(protected service: DocumentService) {}

  private setData(project) {
    this.publicRegistry = this.project.isPublic;
    let cs = this.project.customProperties || {};
    this.enableContentTrust = (cs.__enableContentTrust == 'true');
    this.preventVulnerableImagesFromRunning = (cs.__preventVulnerableImagesFromRunning == 'true');
    this.preventVulnerableImagesFromRunningSeverity = cs.__preventVulnerableImagesFromRunningSeverity || 'medium';
    this.automaticallyScanImagesOnPush = (cs.__automaticallyScanImagesOnPush == 'true');
  }

  ngOnChanges() {
    if (this.project) {
      this.setData(this.project);
    }
  }

  saveProjectConfiguration() {
    let cs = this.project.customProperties || {};
    cs.__enableContentTrust = this.enableContentTrust;
    cs.__preventVulnerableImagesFromRunning = this.preventVulnerableImagesFromRunning;
    cs.__preventVulnerableImagesFromRunningSeverity = this.preventVulnerableImagesFromRunningSeverity;
    cs.__automaticallyScanImagesOnPush = this.automaticallyScanImagesOnPush;

    let projectPatch = {
      isPublic: this.publicRegistry,
      customProperties: cs
    };

    this.service.patch(this.project.documentSelfLink, projectPatch).then(() => {
      this.configForm.markAsPristine();

      this.project.isPublic = this.publicRegistry;
      let cs = this.project.customProperties || {};
      cs.__enableContentTrust = this.enableContentTrust.toString();
      cs.__preventVulnerableImagesFromRunning = this.preventVulnerableImagesFromRunning.toString();
      cs.__preventVulnerableImagesFromRunningSeverity = this.preventVulnerableImagesFromRunningSeverity;
      cs.__automaticallyScanImagesOnPush = this.automaticallyScanImagesOnPush.toString();
      this.project.customProperties = cs;
    }).catch((e) => {
      if (e.status == 304) {
        this.configForm.markAsPristine();
        return;
      }
      console.error(e);
    });
  }

  cancelProjectConfiguration() {
    this.setData(this.project);
    this.configForm.markAsPristine();
  }
}