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

import { OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

/**
 * Component supporting automatic refresh function call based on a configurable interval
 * and refresh function.
 * The auto refresh can be switched on/off.
 */
export class AutoRefreshComponent implements OnInit, OnDestroy {
    protected refreshFn = null;
    protected refreshFnCallScope = null;

    protected set viewShown(value) {
        this.shown = value;
        if (this.shown) {
            this.startRefreshing();
        } else {
            this.stopRefreshing();
        }
    }
    protected get viewShown(): boolean {
        return this.shown;
    }

    private refreshInterval = null;
    private routerSub: Subscription;

    constructor(protected router: Router, protected route: ActivatedRoute,
                private refreshEnabled: boolean = false, private refreshIntervalMS: number = -1,
                private shown:boolean = false) {
    }

    ngOnInit(): void {
        if (this.refreshEnabled) {
            // start automatic refresh
            if (this.viewShown) {
                this.startRefreshing();
            }

            const urlTree = this.router.createUrlTree(['.'], {relativeTo: this.route});
            const currentPath = this.router.serializeUrl(urlTree);

            this.routerSub = this.router.events.subscribe((event) => {
                if (event instanceof NavigationEnd) {
                    if (event.url !== currentPath) {
                        // stop refresh
                        this.stopRefreshing();
                    } else {
                        // restart refresh, if not started
                        if (this.refreshInterval == null) {
                            this.startRefreshing();
                        }
                    }
                }
            });
        }
    }

    ngOnDestroy(): void {
        if (this.refreshEnabled) {
            // stop automatic refresh
            this.stopRefreshing();

            if (this.routerSub) {
                this.routerSub.unsubscribe();
            }
        }
    }

    private startRefreshing() {
        if (this.refreshInterval != null) {
            return;
        }

        if (this.refreshIntervalMS < 0 || this.refreshFn == null
                || this.refreshFnCallScope == null ) {
            console.error('auto refresh arguments not fully available. ' +
                'Auto refresh is not started.');
            return;
        }

        this.refreshInterval = setInterval(() => {
            this.refreshFn.apply(this.refreshFnCallScope);
        }, this.refreshIntervalMS);
    }

    private stopRefreshing() {
        if (this.refreshInterval == null) {
            return;
        }

        clearInterval(this.refreshInterval);
        this.refreshInterval = null;
    }
}
