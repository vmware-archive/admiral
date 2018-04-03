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

import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';
import { BaseDetailsComponent } from './../../../../components/base/base-details.component';
import { DocumentService } from './../../../../utils/document.service';
import { Links } from './../../../../utils/links';
import 'rxjs/add/operator/switchMap';

@Component({
  selector: 'app-kubernetes-cluster-details',
  templateUrl: './kubernetes-cluster-details.component.html',
  styleUrls: ['./kubernetes-cluster-details.component.scss']
})
export class KubernetesClusterDetailsComponent extends BaseDetailsComponent implements OnInit, OnDestroy {

  private sub: any;
  private resourceTabSelected:boolean = false;

  constructor(route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.CLUSTERS);
  }

  protected entityInitialized() {
  }
}
