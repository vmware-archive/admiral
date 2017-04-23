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

import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { DocumentService } from '../../../utils/document.service';
import { Links } from '../../../utils/links';

@Component({
  selector: 'app-template-edit',
  templateUrl: './template-edit.component.html',
  styleUrls: ['./template-edit.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class TemplateEditComponent implements OnInit {

  private editingTemplateName;
  private compositeDescription: any = {};

  private editContainerDefinition;
  private editNetwork;
  private editVolumne;
  private editKubernetes;
  private gridComponents = [
    {
      type: 'new'
    }
  ];

  constructor(protected route: ActivatedRoute, protected service: DocumentService) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
       let id = params['id'];
       if (!id) {
        this.editingTemplateName = true;
       } else {
        this.service.getById(Links.COMPOSITE_DESCRIPTIONS, id).then(compositeDescirption => {
          this.compositeDescription = compositeDescirption;
        });
       }
    });
  }

  get isNewItem(){
    return !this.compositeDescription.documentSelfLink;
  }

  saveTemplateName(value) {
    let updateBody = {
      name: value
    };

    if (this.isNewItem) {
      this.service.post(Links.COMPOSITE_DESCRIPTIONS, updateBody).then((cd) => {
        this.compositeDescription = cd;
      });
    } else {
      this.service.patch(this.compositeDescription.documentSelfLink, updateBody).then((cd) => {
        this.compositeDescription.name = value;
      });
    }
    this.editingTemplateName = false;
  }

  cancelEditTemplateName() {
    this.editingTemplateName = false;
  }

}
