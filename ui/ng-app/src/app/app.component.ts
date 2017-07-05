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
import { Router } from '@angular/router';
import { ViewExpandRequestService } from './services/view-expand-request.service';
import { FT } from './utils/ft';
import { Utils } from './utils/utils';
import { DocumentService } from './utils/document.service';
import { AuthService } from './utils/auth.service';

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

        this.documentService.loadCurrentUserSecurityContext().then((securityContext) => {
            this.userSecurityContext = securityContext;
        }).catch((ex) => {
            console.log(ex);
        });
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

    get userName(): String {
        if (this.userSecurityContext) {
            return this.userSecurityContext.name || this.userSecurityContext.email;
        }
        return null;
    }

    get userNameDetail(): String {
        if (this.userSecurityContext) {
            if (!this.userSecurityContext.name) {
                // Already shown above
                return null;
            }
            return this.userSecurityContext.email;
        }
    }

    logout() {
        this.authService.logout().then(() => {
            window.location.reload();
        });
    }
}
