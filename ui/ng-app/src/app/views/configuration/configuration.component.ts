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

import { Component } from '@angular/core';
import { AuthService } from '../../utils/auth.service';
import { ErrorService } from "../../utils/error.service";
import { Roles } from '../../utils/roles';
import { Utils } from '../../utils/utils';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss']
})
export class ConfigurationComponent {
    private userSecurityContext: any;

    constructor(authService: AuthService, errorService: ErrorService) {

        authService.getCachedSecurityContext().then((securityContext) => {
            this.userSecurityContext = securityContext;
        }).catch((error) => {
            console.error(error);
            errorService.error(Utils.getErrorMessage(error)._generic);
        });
    }

    get hasAdminRole(): boolean {
        return Utils.isAccessAllowed(this.userSecurityContext, null, [Roles.CLOUD_ADMIN]);
    }
}
