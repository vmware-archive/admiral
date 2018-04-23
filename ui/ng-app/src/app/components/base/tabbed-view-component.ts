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

import { OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from "rxjs/Subscription";

const TAB_ID_NONE = "";

/**
 * View supporting tabs.
 */
export class TabbedViewComponent implements OnInit, OnDestroy {
    // tabs for the view
    private tabs: any[];

    // tabs preselection through routing
    routeTabParamSubscription: Subscription;
    activatedTab: string = TAB_ID_NONE;
    tabIdForTabViews: string = TAB_ID_NONE;

    constructor(protected route: ActivatedRoute, protected router: Router) {

    }

    set supportedTabs(supportedTabs: any[]) {
        this.tabs = supportedTabs.concat(TAB_ID_NONE);
    };

    ngOnInit(): void {
        this.routeTabParamSubscription = this.route.params.subscribe((params) => {
            this.activatedTab = params['tab'];

            if (this.activatedTab) {
                this.tabIdForTabViews = TAB_ID_NONE;
            }
        });
    }

    ngOnDestroy() {
        this.routeTabParamSubscription.unsubscribe();
    }

    isActiveTab(tabId: any): boolean {
        return this.activatedTab === tabId;
    }

    tabActivated(isActivated: boolean, tabId: any) {
        if (isActivated) {
            let supportedTab = this.tabs.find((elem) => {
                return elem === tabId;
            });

            if (!supportedTab) {
                console.error('tab not supported for this view', tabId);
            }
        }

        this.routeTab(isActivated, tabId);
    }

    private routeTab(isActivated, currentTab) {
        let urlSegments = this.route.snapshot.url;

        if (isActivated) {
            // tab selection has changed
            if (this.activatedTab !== currentTab) {
                // is previous tab still present in route
                let prevTabString = this.activatedTab;
                let prevTabUrlSegment = urlSegments.find((urlSegment) => {
                    return urlSegment.path.indexOf(prevTabString) > -1;
                });

                let path = '';
                if (prevTabUrlSegment) {
                    path += '../';
                }

                this.activatedTab = currentTab;

                let activeTabString = this.activatedTab;
                let activeTabUrlSegment = urlSegments.find((urlSegment) => {
                    return urlSegment.path.indexOf(activeTabString) > -1;
                });

                if (!prevTabUrlSegment && !activeTabUrlSegment) {
                    // tell subviews what is the current tab selection
                    // this is workaround for clarity tabs and routing issue
                    this.tabIdForTabViews = activeTabString;
                } else {

                    this.tabIdForTabViews = TAB_ID_NONE;

                    let navSubRoute = [];
                    if (path.length > 0) {
                        navSubRoute.push(path);
                    }
                    navSubRoute.push(this.activatedTab);

                    this.router.navigate(navSubRoute, {relativeTo: this.route});
                }
            }
        }
    }
}
