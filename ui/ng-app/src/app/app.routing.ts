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

import { AdministrationComponent } from './views/administration/administration.component';
import { MainResourcesComponent } from './views/main-resources/main-resources.component';

import { IdentityManagementComponent } from './views/identity-management/identity-management.component';
import { ProjectsComponent } from './views/projects/projects.component';
import { ProjectDetailsComponent } from './views/projects/project-details/project-details.component';
import { ProjectCreateComponent } from './views/projects/project-create/project-create.component';
import { ProjectAddMemberComponent } from "./views/projects/project-details/project-add-member.component";
import { RegistriesComponent } from './views/registries/registries.component';
import { ConfigurationComponent } from './views/configuration/configuration.component';
import { LogsComponent } from './views/logs/logs.component';

import { DashboardComponent } from './views/dashboard/dashboard.component';
import { FormerPlaceholderViewComponent } from './views/former-view/former-view.component';
import { RepositoryComponent } from './views/hbr/repository/repository.component';
import { VchClustersComponent } from './views/vch-clusters/vch-clusters.component';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';

import { NavigationContainerType } from './components/navigation-container/navigation-container.component';


export const ROUTES: Routes = [
    {
        path: '', redirectTo: 'home', pathMatch: 'full'
    },
    {
        path: 'home', component: MainResourcesComponent,
        children: [
            {
                path: '', redirectTo: 'applications', pathMatch: 'full'
            },
            {
                path: 'dashboard', component: DashboardComponent
            },
            {
                path: 'project-repositories', component: RepositoryComponent
            },
            {
                path: 'registries', component: RegistriesComponent
            },
            {
                path: 'identity-management', component: IdentityManagementComponent
            },
            {
                path: 'vch-clusters', component: VchClustersComponent
            },
            {
                path: 'kubernetes/pods', component: PodListComponent,
                children: [
                    { path: ':id', component: PodDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                    }}
                ]
            },
            {
                path: 'kubernetes/deployments', component: DeploymentListComponent,
                children: [
                    { path: ':id', component: DeploymentDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                    }}
                ]
            },
            {
                path: 'kubernetes/services', component: ServiceListComponent,
                children: [
                    { path: ':id', component: ServiceDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                    }}
                ]
            },
            {
                 path: '**', component: FormerPlaceholderViewComponent
            }
        ]
    },
    {
        path: 'administration', component: AdministrationComponent,
        children: [
            {
                path: '', redirectTo: 'identity-management', pathMatch: 'full'
            },
            {
                path: 'identity-management', component: IdentityManagementComponent
            },
            {
                path: 'projects', component: ProjectsComponent,
                children: [
                    { path: 'new', component: ProjectCreateComponent, data: {
                        navigationContainerType: NavigationContainerType.Default
                    }},
                    { path: ':id', component: ProjectDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                     }},
                    { path: ':id/edit', component: ProjectCreateComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                     }},
                    { path: ':id/add-member', component: ProjectAddMemberComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                      }}
                ]
            },
            {
                path: 'registries', component: RegistriesComponent
            },
            {
                path: 'configuration', component: ConfigurationComponent
            },
            {
                path: 'logs', component: LogsComponent
            }
        ]
    }
];

export const ROUTING: ModuleWithProviders = RouterModule.forRoot(ROUTES, {useHash: true});
