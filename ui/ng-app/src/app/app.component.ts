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

import { Component } from '@angular/core';
import { ViewExpandRequestService } from './services/view-expand-request.service';
import { FT } from './utils/ft';
import { Utils } from './utils/utils';
import { DocumentService } from './utils/document.service';
import { AuthService } from './utils/auth.service';
import { RoutesRestriction } from './utils/routes-restriction';

@Component({
    selector: 'my-app',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent {
    fullScreen: boolean;
    userSecurityContext: any;

    constructor(private viewExpandRequestor: ViewExpandRequestService, private documentService: DocumentService,
    private authService: AuthService) {
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
    }

    get embedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    get isLogin(): boolean {
        return Utils.isLogin();
    }

    get compute(): boolean {
        return FT.isCompute();
    }

    get vic(): boolean {
        return FT.isVic();
    }

    get admiral(): boolean {
        return !this.compute && !this.vic;
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

    get administrationRouteRestriction() {
        return RoutesRestriction.ADMINISTRATION;
    }
}
