/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, AfterViewInit, OnChanges, Input, Output, EventEmitter, ViewEncapsulation, ViewChild, ElementRef } from '@angular/core';
import { Search } from 'admiral-ui-common';

const NA = 'N/A';

@Component({
  selector: 'search',
  template: '<div #holder></div>',
  styleUrls: ['./search.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class SearchComponent implements AfterViewInit, OnChanges {

  @Input()
  queryOptions: any;

  @Input()
  placeholder: string;

  @Input()
  searchPlaceholder: string;

  @Input()
  suggestionProperties: Array<string>;

  @Input()
  occurrenceProperties: Array<any>;

  @Output()
  searchChange: EventEmitter<any> = new EventEmitter<any>();

  @ViewChild('holder')
  elHolder: ElementRef;

  private search: Search;

  constructor() { }

  public ngAfterViewInit() {
    var searchProperties = {
      suggestionProperties: this.suggestionProperties,
      occurrences: this.occurrenceProperties,
      placeholderHint: this.placeholder
    };

    this.search = new Search(searchProperties, () => {
      this.searchChange.emit(this.getQueryOptions());
    });
    if (this.queryOptions) {
      this.search.setQueryOptions(this.queryOptions);
    }
    this.elHolder.nativeElement.appendChild(this.search.getEl());
  }

  public getQueryOptions() {
    return this.search.getQueryOptions();
  }

  public ngOnChanges(changes) {
    if (this.search) {
      this.search.setQueryOptions(this.queryOptions);
    }
  }
}


