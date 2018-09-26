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


import { Directive, Input, ElementRef, Renderer, OnChanges } from '@angular/core';
import { AuthService } from '../../utils/auth.service';
import { Utils } from '../../utils/utils';


@Directive({
  selector: '[allowNavigation]'
})
export class AllowNavigationDirective implements OnChanges {
  @Input()
  roles: string[];

  @Input()
  projectSelfLink: string;

  constructor(private el: ElementRef, private renderer: Renderer,
              private authService: AuthService) {
  }

  ngOnChanges(changes) {
    this.allowAccess();
  }

  private allowAccess() {
    this.authService.getCachedSecurityContext().then((securityContext) => {
      let show = Utils.isAccessAllowed(securityContext, this.projectSelfLink, this.roles);

      if (!show) {
        this.renderer.setElementStyle(this.el.nativeElement, 'display', 'none');
      }
    }).catch((ex) => {
      console.error(ex);
    });
  }
}
