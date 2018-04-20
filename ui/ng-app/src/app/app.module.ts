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
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NgModule, APP_INITIALIZER } from '@angular/core';
import { Injectable } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { HttpModule } from '@angular/http';
import { ClarityModule } from 'clarity-angular';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { CookieModule } from 'ngx-cookie';
import { AppComponent } from './app.component';
import { ROUTING } from "./app.routing";
import { Ajax, SessionTimedOutSubject } from './utils/ajax.service';
import { DocumentService } from './utils/document.service';
import { ErrorService } from './utils/error.service';
import { ProjectService } from './utils/project.service';
import { AuthService } from './utils/auth.service';
import { TemplateService } from './utils/template.service';
import { ViewExpandRequestService } from './services/view-expand-request.service';
import { HarborLibraryModule, SERVICE_CONFIG, IServiceConfig } from 'harbor-ui';
import * as I18n from 'i18next';
import { FT } from './utils/ft';
import { HomeAuthGuard } from 'app/services/home-auth-guard.service';
import { AdminAuthGuard } from 'app/services/admin-auth-guard.service';

import { ADMIRAL_DECLARATIONS } from './admiral';
import { initHarborConfig } from './init-harbor-config'

@NgModule({
    declarations: ADMIRAL_DECLARATIONS,
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        FormsModule,
        ReactiveFormsModule,
        HttpModule,
        ClarityModule.forRoot(),
        CookieModule.forRoot(),
        ROUTING,
        HarborLibraryModule.forRoot({
            config: {
                provide: SERVICE_CONFIG,
                useFactory: initHarborConfig
            }
        }),
        InfiniteScrollModule,
    ],
    providers: [
        Ajax,
        HomeAuthGuard,
        AdminAuthGuard,
        SessionTimedOutSubject,
        DocumentService,
        ProjectService,
        AuthService,
        TemplateService,
        ViewExpandRequestService,
        ErrorService
    ],
    bootstrap: [AppComponent]
})
export class AppModule {

    constructor(router: Router) {

        router.events.subscribe((val) => {
            if (val instanceof NavigationEnd && window.parent !== window) {
                if ((<any>window).notifyNavigation) {
                    (<any>window).notifyNavigation(window.location.hash.substr(1));
                }
            }
        });
    }
}
