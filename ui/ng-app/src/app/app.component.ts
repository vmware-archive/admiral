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
import { DocumentService } from './utils/document.service';

@Component({
    selector: 'my-app',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent {
    expanded: boolean;
    fullScreen: boolean;
    user: any;

    constructor(private viewExpandRequestor: ViewExpandRequestService, private documentService: DocumentService) {
        this.viewExpandRequestor.getExpandRequestEmitter().subscribe(isExpand => {
            this.expanded = isExpand;
        });

        this.viewExpandRequestor.getFullScreenRequestEmitter().subscribe(isFullScreen => {
            this.fullScreen = isFullScreen;
        });

        this.documentService.loadCurrentUser().then((userState) => {
            return this.documentService.getPrincipalById(userState.email);
        }).then((principal) => {
            this.user = principal;
        }).catch((ex) => {
            console.log(ex);
            this.user = {
                name: "problem1"
            };
        });
    }

    get embedded(): boolean {
        return FT.isApplicationEmbedded();
    }

    get userName(): String {
        if (this.user) {
            return this.user.name || this.user.email;
        }
        return '--';
    }

    get userNameDetail(): String {
        if (this.user) {
            if (!this.user.name) {
                // Already shown above
                return null;
            }
            return this.user.email;
        }
    }
}
