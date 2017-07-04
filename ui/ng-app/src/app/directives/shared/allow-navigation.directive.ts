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

import { AuthService } from './../../utils/auth.service';
import { Directive, Input, Inject, ElementRef, Renderer } from '@angular/core';

@Directive({
  selector: '[allowNavigation]'
})
export class AllowNavigationDirective {

  @Input()
  roles: string[];

  constructor(private el: ElementRef, private renderer: Renderer, private authService: AuthService) {
  }

  ngOnInit(){
    this.authService.loadCurrentUserSecurityContext().then((securityContext) => {
      let show = false;

      if (securityContext && securityContext.roles) {
        securityContext.roles.forEach(element => {
          if (this.roles.indexOf(element) != -1) {
            show = true;
            return;
          }
        });
      }

      this.renderer.setElementStyle(this.el.nativeElement, 'display', show ? 'block' : 'none');
    });
  }
}