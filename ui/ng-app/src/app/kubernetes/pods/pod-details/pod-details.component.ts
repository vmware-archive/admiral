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
import { DocumentService } from '../../../utils/document.service';
import { Links } from '../../../utils/links';

@Component({
  selector: 'pod-details',
  templateUrl: './pod-details.component.html',
  styleUrls: ['./pod-details.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class PodDetailsComponent implements OnInit {

  private id;
  private pod;
  private podLogs;
  private podLogsTimeout;

  constructor(private route: ActivatedRoute, private service: DocumentService) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
       this.id = params['id'];
       this.service.getById(Links.PODS, this.id).then(pod => {
         this.pod = pod;
       });

       this.getLogs();

       clearInterval(this.podLogsTimeout);
       this.podLogsTimeout = setInterval(this.getLogs, )
    });
  }

  ngOnDestroy() {
    clearInterval(this.podLogsTimeout);
  }

  getLogs() {
    this.service.getLogs(this.id, 10000).then(logs => {
      this.podLogs = logs;
    });
  }
}
