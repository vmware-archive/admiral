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

import { Component,  } from '@angular/core';

@Component({
  selector: 'back-button',
  template: `<a class="close-button"><i class="fa fa-chevron-left"></i><span class="backtext"><ng-content></ng-content></span></a>`,
  styleUrls: ['./back-button.component.scss']
})
export class BackButtonComponent {
  constructor() { }
}


