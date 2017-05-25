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
import { slideAndFade } from '../../../utils/transitions';
import { Links } from '../../../utils/links';
import { Router } from '@angular/router';
import { DocumentService } from '../../../utils/document.service';
import { DeploymentDetailsComponent } from '../details/deployment-details.component';
import { NavigationContainerType } from '../../../components/navigation-container/navigation-container.component';

@Component({
  selector: 'deployment-list',
  templateUrl: './deployment-list.component.html',
  styleUrls: ['./deployment-list.component.scss'],
  animations: [slideAndFade()]
})
export class DeploymentListComponent {
  serviceEndpoint = Links.DEPLOYMENTS;
  navigationContainerTypePerComponent = {
    [<any>DeploymentDetailsComponent]: NavigationContainerType.Fullscreen
  }
}
