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

import { ModuleWithProviders } from '@angular/core/src/metadata/ng_module';
import { Routes, RouterModule } from '@angular/router';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';


export const ROUTES: Routes = [
    {
        path: '', redirectTo: 'kubernetes/pods', pathMatch: 'full'
    },
    {
        path: 'kubernetes/pods', component: PodListComponent,
        children: [
            { path: ':id', component: PodDetailsComponent }
        ]
    },
    {
        path: 'kubernetes/deployments', component: DeploymentListComponent,
        children: [
            { path: ':id', component: DeploymentDetailsComponent }
        ]
    },
    {
        path: 'kubernetes/services', component: ServiceListComponent,
        children: [
            { path: ':id', component: ServiceDetailsComponent }
        ]
    }
];

export const ROUTING: ModuleWithProviders = RouterModule.forRoot(ROUTES, {useHash: true});
