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

import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Links } from '../../../utils/links';
import { DocumentService } from '../../../utils/document.service';

@Component({
  selector: 'app-cluster-create',
  templateUrl: './cluster-create.component.html',
  styleUrls: ['./cluster-create.component.scss']
})
/**
 * Modal for cluster creation.
 */
export class ClusterCreateComponent implements AfterViewInit, OnInit {
  opened: boolean;
  isEdit: boolean;
  selectedCredentials: any;
  credentials: any[];

  clusterForm = new FormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
    url: new FormControl('', Validators.required),
    credentials: new FormControl('')
  });

  constructor(private router: Router, private route: ActivatedRoute, private service: DocumentService) {}

  ngOnInit() {
    this.route.queryParams.subscribe(queryParams => {
      this.service.list(Links.CREDENTIALS, queryParams).then(credentials => {
        this.credentials = credentials.documents;
      });
    });
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.opened = true;
    });
  }

  toggleModal(open) {
    this.opened = open;
    if (!open) {
      this.router.navigate(['../'], { relativeTo: this.route });
    }
  }

  saveCluster() {
    // TODO: implement save
  }

}