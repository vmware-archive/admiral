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

import { FT } from './../../utils/ft';
import { Component, OnInit, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'app-main-resources',
  templateUrl: './main-resources.component.html',
  styleUrls: ['./main-resources.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComponent implements OnInit {

    kubernetesEnabled = FT.isKubernetesHostOptionEnabled();

    embeddedMode = FT.isApplicationEmbedded();

    constructor() { }

    ngOnInit() {
    }
}
