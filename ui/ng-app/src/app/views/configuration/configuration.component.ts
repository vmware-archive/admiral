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

import { Roles } from './../../utils/roles';
import { AuthService } from './../../utils/auth.service';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss']
})
export class ConfigurationComponent implements OnInit {

  hasAdminRole: boolean;

  constructor(private authService: AuthService) { 
    
  }

  ngOnInit() {
    this.setIsAdmin();
  }

  private setIsAdmin() {
    
    this.authService.getCachedSecurityContext().then(securityContext => {
      let isAdmin = false;
      if (securityContext && securityContext.roles) {
        for (var index = 0; index < securityContext.roles.length; index++) {
          var role = securityContext.roles[index];
          if (Roles.CLOUD_ADMIN === role) {
            isAdmin = true;
            break;
          }
        }
      }
      this.hasAdminRole = isAdmin;
    });

  }
}
