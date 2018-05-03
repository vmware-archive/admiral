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

import { Component } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { DocumentService } from '../../../../utils/document.service';

@Component({
  selector: 'app-kubernetes-cluster-add',
  templateUrl: './kubernetes-cluster-add.component.html',
  styleUrls: ['./kubernetes-cluster-add.component.scss']
})
/**
 * View for adding existing kubernetes clusters.
 */
export class KubernetesClusterAddComponent {

    constructor(private router: Router, private route: ActivatedRoute, private service: DocumentService) {}
}
