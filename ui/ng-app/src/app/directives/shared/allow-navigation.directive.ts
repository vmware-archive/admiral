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
import { Utils } from './../../utils/utils';
import { OnChanges } from '@angular/core';
import { FT } from './../../utils/ft';

@Directive({
  selector: '[allowNavigation]'
})
export class AllowNavigationDirective implements OnChanges {

  @Input()
  roles: string[];

  @Input()
  projectSelfLink: string;

  constructor(private el: ElementRef, private renderer: Renderer, private authService: AuthService) {
  }

  ngOnChanges(changes) {
    this.allowAccess();
  }

  private allowAccess() {
    if (FT.isApplicationEmbedded()) {
      this.renderer.setElementStyle(this.el.nativeElement, 'display', 'block');
      return;
    }

    this.authService.loadCurrentUserSecurityContext().then((securityContext) => {
      let show = false;
      if (securityContext && securityContext.roles) {
        securityContext.roles.forEach(role => {
          if (this.roles.indexOf(role) != -1) {
            show = true;
            return;
          }
        });
      }

      if (securityContext && securityContext.projects) {
        securityContext.projects.forEach(project => {
          if (project && project.roles) {
            project.roles.forEach(role => {
              if (this.projectSelfLink) {
                if (project.documentSelfLink === this.projectSelfLink && this.roles.indexOf(role) != -1) {
                  show = true;
                  return;
                }
              } else {
                if (this.roles.indexOf(role) != -1) {
                  show = true;
                  return;
                }
              }
            });
          }
        });
      }

      this.renderer.setElementStyle(this.el.nativeElement, 'display', show ? 'block' : 'none');
    }).catch((ex) => {
      // show in case of no authentication
      this.renderer.setElementStyle(this.el.nativeElement, 'display', 'block');
    });
  }
}