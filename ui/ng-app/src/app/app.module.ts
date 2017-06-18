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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Router, NavigationEnd } from '@angular/router';
import { HttpModule } from '@angular/http';
import { ClarityModule } from 'clarity-angular';
import { InfiniteScrollModule } from 'ngx-infinite-scroll';
import { CookieModule } from 'ngx-cookie';
import { AppComponent } from './app.component';
import { ROUTING } from "./app.routing";
import { Ajax, SessionTimedOutSubject } from './utils/ajax.service';
import { DocumentService } from './utils/document.service';
import { ProjectService } from './utils/project.service';
import { TemplateService } from './utils/template.service';
import { ViewExpandRequestService } from './services/view-expand-request.service';
import { HarborLibraryModule, SERVICE_CONFIG, IServiceConfig } from 'harbor-ui';
import * as I18n from 'i18next';

import { ADMIRAL_DECLARATIONS } from './admiral';

export const ServiceConfig:IServiceConfig = {
    systemInfoEndpoint: "/hbr-api/system",
    repositoryBaseEndpoint: "/hbr-api/repositories",
    logBaseEndpoint: "/hbr-api/logs",
    targetBaseEndpoint: "/hbr-api/targets",
    replicationRuleEndpoint: "/hbr-api/policies/replication",
    replicationJobEndpoint: "/hbr-api/jobs/replication",
    enablei18Support: true,
    langMessageLoader: "http",
    langMessagePathForHttpLoader: "/hbr-api/i18n/lang/"
};


let HBR_SUPPORTED_LANGS = ['en-us', 'zh-cn', 'es-es'];

export function initConfig(ts: TranslateService) {
    return () => {
        let lng = I18n.language || 'en-us';
        ts.addLangs(HBR_SUPPORTED_LANGS);
        ts.use(lng.toLocaleLowerCase());
    };
}


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
        HarborLibraryModule.forChild({
            config: {
                provide: SERVICE_CONFIG,
                useValue: ServiceConfig
            }
        }),
        InfiniteScrollModule,
    ],
    providers: [
        Ajax,
        SessionTimedOutSubject,
        DocumentService,
        ProjectService,
        TemplateService,
        ViewExpandRequestService,
        TranslateService,
        {
            provide: APP_INITIALIZER,
            useFactory: initConfig,
            deps: [TranslateService],
            multi: true
        },
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
