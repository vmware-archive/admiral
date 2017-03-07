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

import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BaseListComponent } from '../../../components/base/base-list.component';
import { slideAndFade } from '../../../utils/transitions';
import { Links } from '../../../utils/links';
import { DocumentService } from '../../../utils/document.service';

@Component({
  selector: 'service-list',
  templateUrl: './service-list.component.html',
  styleUrls: ['./service-list.component.scss'],
  animations: [slideAndFade()]
})
export class ServiceListComponent extends BaseListComponent {
  constructor(service: DocumentService, router: Router) {
    super(service, router, Links.SERVICES);
  }
}