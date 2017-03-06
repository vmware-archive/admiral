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

import { Component, OnChanges, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'logs-scroll',
  template: '<div #logsHolder>{{logs}}</div>'
})
export class LogsScrollComponent implements OnChanges {

  @ViewChild('logsHolder') logsHolder;

  @Input()
  logs: string;

  constructor() { }

  public ngOnChanges(changes) {
    if (changes) {
      var scrolledToBottom = (this.logsHolder.nativeElement.scrollTop / this.logsHolder.nativeElement.scrollHeight) > 0.95;
      if (scrolledToBottom) {
        this.logsHolder.nativeElement.scrollTop = this.logsHolder.nativeElement.scrollHeight;
      }
    }
  }
}
