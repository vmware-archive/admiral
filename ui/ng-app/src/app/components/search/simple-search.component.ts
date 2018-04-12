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

import { Component, AfterViewInit, OnChanges, Input, Output, EventEmitter, ViewEncapsulation,
    ViewChild, ElementRef } from '@angular/core';
import { SimpleSearch } from "admiral-ui-common";

@Component({
    selector: 'simple-search',
    template: '<div #simpleSearchHolder></div>',
    styleUrls: ['./simple-search.component.scss'],
    encapsulation: ViewEncapsulation.None
})
export class SimpleSearchComponent implements AfterViewInit, OnChanges {
    @Input()
    displayPropertyName: string;

    @Output()
    searchChange: EventEmitter<any> = new EventEmitter<any>();
    @Output()
    searchSelectionChange: EventEmitter<any> = new EventEmitter<any>();

    @ViewChild('simpleSearchHolder')
    elHolder: ElementRef;

    search: SimpleSearch;

    constructor() { }

    public set value(value:string) {
        if (this.search) {
            this.search.setValue(value);
        }
    }

    public ngAfterViewInit() {
        // NOTE: the results are expected in format:
        // [{displayPropertyName: '', name: '', ...}, {},...]
        this.search = new SimpleSearch(this.displayPropertyName,
            (query, callback, asyncCallback) => {
                this.searchChange.emit({
                    query: query,
                    callback: asyncCallback
                });
            }, (obj, datum, name) => {
                this.searchSelectionChange.emit({
                    object: obj,
                    datum: datum,
                    name: name
                });
                this.search.setValue('');
            });

        this.elHolder.nativeElement.appendChild(this.search.getEl());
    }

    public ngOnChanges(changes) {
    }
}
