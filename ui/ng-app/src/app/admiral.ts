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

import { AppComponent } from './app.component';
import { TranslatePipe } from './utils/i18n.pipe';
import { HomeComponent } from "./home/home.component";
import { AboutComponent } from "./about/about.component";
import { StatsComponent } from "./stats/stats.component";

export const ADMIRAL_DECLARATIONS = [
  AppComponent,
  TranslatePipe,
  HomeComponent,
  AboutComponent,
  StatsComponent
];
