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

import {trigger, state, animate, style, transition} from '@angular/core';

export function slideAndFade() {
  return trigger('slideAndFade', [
    state('*', style({
      opacity: '0',
      'pointer-events': 'none'
    })),
    state('active', style({
      opacity: '1',
      'pointer-events': 'initial'
    })),
    transition('* => active', [
      style({
        opacity: '1',
        transform: 'translateX(100%)'
      }),
      animate('250ms ease-in', style({transform: 'translateX(0%)'}))
    ]),
    transition('active => *', [
      style({
        opacity: '1',
        transform: 'translateX(0%)'
      }),
      animate('200ms ease-in', style({opacity: '0'}))
    ])
  ]);
}