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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormControl, FormGroup } from "@angular/forms";
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subscription } from "rxjs/Subscription";

import { searchConstants, searchUtils } from "admiral-ui-common";

@Component({
    selector: 'table-view',
    templateUrl: './table-view.component.html',
    styleUrls: ['./table-view.component.scss']
})
/**
 * Main component for table views.
 */
export class TableViewComponent implements OnInit {
    // In
    @Input() searchPlaceholder: string = '';
    // Out
    @Output() onRefresh: EventEmitter<any> = new EventEmitter();

    querySub: Subscription;
    routerSub: Subscription;

    // Search
    searchQueryOptions: any;

    searchForm = new FormGroup({
        occurrenceSelector: new FormControl({value: searchConstants.SEARCH_OCCURRENCE.ALL, disabled: true}),
        searchGridInput: new FormControl('')
    });
    occurrenceSelection: any = searchConstants.SEARCH_OCCURRENCE.ALL;

    // Creation
    constructor(private router: Router, private route: ActivatedRoute) {
        //
    }

    ngOnInit() {
        const urlTree = this.router.createUrlTree(['.'], { relativeTo: this.route });
        const currentPath = this.router.serializeUrl(urlTree);

        this.routerSub = this.router.events.subscribe((event) => {
            if (event instanceof NavigationEnd && event.url === currentPath) {
                this.onRefresh.emit({
                    queryOptions: this.searchQueryOptions
                });
            }
        });

        this.querySub = this.route.queryParams.subscribe(queryParams => {
            this.searchQueryOptions = queryParams;
            let searchString = searchUtils.getSearchString(queryParams).trim();
            this.searchForm.get('searchGridInput').setValue(searchString);

            // reset to initial view (starting page 1)
            this.onRefresh.emit({
                queryOptions: this.searchQueryOptions
            });
        });
    }

    onSearch($event) {
        let searchString = this.searchForm.get("searchGridInput").value;
        let queryOptions: any = searchUtils.getQueryOptions(searchString, this.occurrenceSelection);

        this.router.navigate(['.'], {
            relativeTo: this.route,
            queryParams: queryOptions
        });
    }

    refresh() {
        this.onRefresh.emit({
            queryOptions: this.searchQueryOptions
        });
    }
}
