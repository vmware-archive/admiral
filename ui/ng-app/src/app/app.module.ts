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

import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule }          from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { HttpModule } from '@angular/http';
import { ClarityModule } from 'clarity-angular';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { AppComponent } from './app.component';
import { ROUTING } from "./app.routing";
import { Ajax, SessionTimedOutSubject } from './utils/ajax.service';
import { DocumentService } from './utils/document.service';
import { ViewExpandRequestService } from './services/view-expand-request.service';

import { ADMIRAL_DECLARATIONS } from './admiral';

@NgModule({
    declarations: ADMIRAL_DECLARATIONS,
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        ReactiveFormsModule,
        HttpModule,
        ClarityModule.forRoot(),
        ROUTING,
        InfiniteScrollModule
    ],
    providers: [
        Ajax,
        SessionTimedOutSubject,
        DocumentService,
        ViewExpandRequestService
    ],
    bootstrap: [AppComponent]
})
export class AppModule {

    constructor(router: Router) {
        router.events.subscribe((val) => {
            if (val instanceof NavigationEnd && window.parent !== window) {
                window.parent.location.hash = window.location.hash;
            }
        });
    }
}
