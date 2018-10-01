/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { slideAndFade } from '../../utils/transitions';

@Component({
    selector: 'navigation-container',
    template: `<div [ngStyle]="contentStyle" 
                    [ngClass]="{'full-screen': type === 'fullScreenSlide', 'with-back-button': showBackButton }">
            <ng-content></ng-content>
        </div>`,
    animations: [slideAndFade()]
})
/**
 * Container allowing navigation, using sliding effect. Has back button support.
 */
export class NavigationContainerComponent implements OnInit, OnDestroy {

    @ViewChild('contentHolder') contentHolder;

    private oldComponent: string;
    private routeObserve: Subscription;
    private hideBackButton: boolean;

    contentStyle: any = {
        opacity: '0',
        pointerEvents: 'none',
        transition: 'none'
    };

    type: string;

    constructor(private router: Router, private route: ActivatedRoute,
                private viewExpandRequester: ViewExpandRequestService) {

    }

    ngOnInit() {
        this.routeObserve = this.router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {

                let child = {
                    parent: this.route
                };

                if (this.route.children.length != 0) {
                    child = this.route.children[0];
                }

                this.handleNewComponent(child);
            }
        });
    }

    get showBackButton() {
        return this.type === 'fullScreenSlide' && !this.hideBackButton;
    }

    hasFullscreenParent(route) {
        var routeData = route.data && route.data.value;
        if (routeData && routeData.navigationContainerType === NavigationContainerType.Fullscreen) {
            return true;
        }

        if (!route.parent) {
            return false;
        }

        return this.hasFullscreenParent(route.parent);
    }

    handleNewComponent(newRoute) {
        var newComponent: any = newRoute.component;
        var navigationContainerType = newRoute.data && newRoute.data.value
            && newRoute.data.value.navigationContainerType;
        this.hideBackButton = newRoute.data && newRoute.data.value
            && newRoute.data.value.hideBackButton;

        let selectedType;
        if (newComponent != this.oldComponent) {
            this.oldComponent = newComponent;
            selectedType = navigationContainerType || NavigationContainerType.None;
            this.type = selectedType.toString();

            if (selectedType === NavigationContainerType.None) {
                this.contentStyle.opacity = '0';
                this.contentStyle.pointerEvents = 'none';
            } else {
                if (selectedType === NavigationContainerType.Fullscreen) {
                    this.contentStyle.transition = 'all 0.3s ease-in';
                } else {
                    this.contentStyle.transition = 'none';
                }
                this.contentStyle.opacity = '1';
                this.contentStyle.pointerEvents = 'all';
            }
        }

        let isFullscreen = this.hasFullscreenParent(newRoute);
        this.viewExpandRequester.requestFullScreen(isFullscreen);
    }

    ngOnDestroy() {
        if (this.routeObserve) {
            this.routeObserve.unsubscribe();
        }

        let isFullscreen = this.hasFullscreenParent(this.route);
        this.viewExpandRequester.requestFullScreen(isFullscreen);
    }
}

export enum NavigationContainerType {
    Fullscreen = <any>'fullScreenSlide',
    Default = <any>'default',
    None = <any>'none'
}
