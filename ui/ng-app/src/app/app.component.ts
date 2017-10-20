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

import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ViewExpandRequestService } from './services/view-expand-request.service';
import { FT } from './utils/ft';
import { Utils } from './utils/utils';
import { DocumentService } from './utils/document.service';
import { AuthService } from './utils/auth.service';
import { RoutesRestriction } from './utils/routes-restriction';
import { SessionTimedOutSubject } from './utils/ajax.service';

@Component({
    selector: 'my-app',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
    fullScreen: boolean;
    userSecurityContext: any;
    showSessionTimeout: boolean;

    constructor(private viewExpandRequestor: ViewExpandRequestService,
        private documentService: DocumentService,
        private authService: AuthService,
        private sessionTimedOutSubject: SessionTimedOutSubject,
        private cd: ChangeDetectorRef,
        private title: Title) {
        this.viewExpandRequestor.getFullScreenRequestEmitter().subscribe(isFullScreen => {
            this.fullScreen = isFullScreen;
        });

        if(!this.embedded) {
            this.authService.getCachedSecurityContext().then((securityContext) => {
                this.userSecurityContext = securityContext;
            }).catch((ex) => {
                console.log(ex);
            });
        }

        this.sessionTimedOutSubject.subscribe(e => {
            this.showSessionTimeout = !FT.isApplicationEmbedded();
            // Since anyone can call sessionTimedOutSubject,
            // an update can happen outside of the Angular Zone and would not
            // detect a change, therefore call it manually
            this.cd.detectChanges();
        });
    }

    get embedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    get isLogin(): boolean {
        return Utils.isLogin();
    }

    get vic(): boolean {
        return FT.isVic();
    }

    get admiral(): boolean {
        return !this.vic;
    }

    get userName(): String {
        if (this.userSecurityContext) {
            return this.userSecurityContext.name || this.userSecurityContext.id;
        }
        return null;
    }

    get userNameDetail(): String {
        if (this.userSecurityContext) {
            if (!this.userSecurityContext.name) {
                // Already shown above
                return null;
            }
            return this.userSecurityContext.id;
        }
    }

    logout() {
        this.authService.logout().then((location) => {
            if (location !== null) {
                window.location.href = location;
            } else {
                window.location.reload();
            }
        }, (e) => {
            console.log(e);
        });
    }

    reload() {
        window.location.reload();
    }

    ngOnInit() {
        if (!this.embedded) {
            if (this.vic) {
                this.title.setTitle("vSphere Integrated Containers");
                document.getElementById('appFavicon').setAttribute('href', '../assets/images/vic-favicon.ico');
            } else {
                this.title.setTitle("Admiral");
                document.getElementById('appFavicon').setAttribute('href', '../assets/images/favicon.ico');
            }
        }
    }

    get administrationRouteRestriction() {
        return RoutesRestriction.ADMINISTRATION;
    }
}
