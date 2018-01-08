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

import { Component, ViewChild, ViewEncapsulation, Input, Output, EventEmitter } from '@angular/core';

import { AuthService } from '../../utils/auth.service';
import { ProjectService } from "../../utils/project.service";
import { SessionTimedOutSubject } from '../../utils/ajax.service';
import { RoutesRestriction } from '../../utils/routes-restriction';
import { Utils } from '../../utils/utils';

@Component({
  selector: 'former-view',
  templateUrl: './former-view.component.html',
  styleUrls: ['./former-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
/**
 * Wrapper component for the non-angular views.
 */
export class FormerViewComponent {
  private url: string;
  private frameLoading = false;

  @ViewChild('admiralContentFrame') admiralContentFrame;

  @Input()
  forCompute: boolean;

  @Output()
  onRouteChange: EventEmitter<string> = new EventEmitter();

  constructor(private sessionTimedOutSubject: SessionTimedOutSubject,
              private authService: AuthService, private projectService: ProjectService) {

    projectService.activeProject.subscribe((value) => {
      this.adaptSubViewAccess();
    });
  }

  @Input()
  set path(val: string) {
    if (!val) {
      // no path to view provided, skipping frame loading
      return;
    }

    this.url = window.location.pathname + 'ogui/index-no-navigation.html';

    if (this.forCompute) {
      this.url += '?compute';
    }

    this.url += '#' + val;

    if (Utils.isLogin()) {
      return;
    }

    let iframeEl = this.admiralContentFrame.nativeElement;
    if (!iframeEl.src) {
      this.frameLoading = true;

      iframeEl.onload = () => {
        this.authService.getCachedSecurityContext().then((securityContext) => {
          iframeEl.contentWindow.authSession = securityContext;
        });

        iframeEl.contentWindow.isAccessAllowed = Utils.isAccessAllowed;
        iframeEl.contentWindow.routesRestrictions = RoutesRestriction;

        this.frameLoading = false;
        iframeEl.src = this.url;

        iframeEl.contentWindow.notifyNavigation = (hash) => {
          this.onRouteChange.emit(hash);
        };

        iframeEl.contentWindow.notifySessionTimeout = () => {
          this.sessionTimedOutSubject.setError({});
        };
      };

      iframeEl.src = this.url;

    } else if (!this.frameLoading) {

      iframeEl.src = this.url;
    }
  }

  adaptSubViewAccess() {
    const ROUTE_EDIT_TEMPLATE = 'templates/template/';
    const ROUTE_TEMPLATES = 'templates?$category=templates';

    if (this.url && this.url.indexOf(ROUTE_EDIT_TEMPLATE) > -1) {
      this.path = ROUTE_TEMPLATES;
    }
  }
}

@Component({
  selector: 'former-view-placeholder',
  template: '<div></div>'
})
export class FormerPlaceholderViewComponent {
}
