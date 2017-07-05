import { TagDetailsComponent } from './views/tag-details/tag-details.component';
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
import { RoutesRestriction } from './utils/routes-restriction';

import { AdministrationComponent } from './views/administration/administration.component';
import { MainResourcesComponent } from './views/main-resources/main-resources.component';
import { MainResourcesComputeComponent } from './views/main-resources-compute/main-resources-compute.component';

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
import { ClustersComponent } from './views/clusters/clusters.component';
import { ClusterDetailsComponent } from './views/clusters/cluster-details/cluster-details.component';
import { ClusterCreateComponent } from './views/clusters/cluster-create/cluster-create.component';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';

import { NavigationContainerType } from './components/navigation-container/navigation-container.component';
import { LoginComponent } from './components/login/login.component';

import { AuthGuard } from './services/auth-guard.service';

// compute views
import { InstanceTypesComponent } from './views/profiles/instance-types/instance-types.component';

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
                path: 'project-repositories', component: RepositoryComponent,
                children: [
                    { path: 'repositories/:rid/tags/:tid', component: TagDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen,
                        hideBackButton: true
                    }},
                ]
            },
            {
                path: 'registries', component: RegistriesComponent
            },
            {
                path: 'identity-management', component: IdentityManagementComponent
            },
            {
                path: 'clusters', component: ClustersComponent,
                children: [
                    { path: 'new', component: ClusterCreateComponent, data: {
                        navigationContainerType: NavigationContainerType.Default
                    }},
                    { path: ':id', component: ClusterDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                    }},
                    { path: ':id/edit', component: ClusterCreateComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen
                    }}
                ]
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
        canActivate: [AuthGuard],
        data: { roles: RoutesRestriction.ADMINISTRATION },
        children: [
            {
                path: '', redirectTo: 'projects', pathMatch: 'full'
            },
            {
                path: 'identity-management', component: IdentityManagementComponent,
                canActivate: [AuthGuard],
                data: { roles: RoutesRestriction.IDENTITY_MANAGEMENT }
            },
            {
                path: 'projects', component: ProjectsComponent,
                canActivate: [AuthGuard],
                data: { roles: RoutesRestriction.PROJECTS },
                children: [
                    { path: 'new', component: ProjectCreateComponent,
                        canActivate: [AuthGuard],
                        data: {
                            navigationContainerType: NavigationContainerType.Default,
                            roles: RoutesRestriction.PROJECTS_NEW
                        }
                    },
                    { path: ':id', component: ProjectDetailsComponent,
                        canActivate: [AuthGuard],
                        data: {
                            navigationContainerType: NavigationContainerType.Fullscreen,
                            roles: RoutesRestriction.PROJECTS_ID
                        }
                    },
                    { path: ':id/edit', component: ProjectCreateComponent,
                        canActivate: [AuthGuard],
                        data: {
                            navigationContainerType: NavigationContainerType.Fullscreen,
                            roles: RoutesRestriction.PROJECTS_ID_EDIT
                        }
                    },
                    { path: ':id/repositories/:rid/tags/:tid', component: TagDetailsComponent, data: {
                        navigationContainerType: NavigationContainerType.Fullscreen,
                        hideBackButton: true
                     }},
                    { path: ':id/add-member', component: ProjectAddMemberComponent,
                        canActivate: [AuthGuard],
                        data: {
                            navigationContainerType: NavigationContainerType.Fullscreen,
                            roles: RoutesRestriction.PROJECTS_ID_ADD_MEMBER
                        }
                    }
                ]
            },
            {
                path: 'registries', component: RegistriesComponent,
                canActivate: [AuthGuard],
                data: { roles: RoutesRestriction.REGISTRIES }
            },
            {
                path: 'configuration', component: ConfigurationComponent,
                canActivate: [AuthGuard],
                data: { roles: RoutesRestriction.CONFIGURATION }
            },
            {
                path: 'logs', component: LogsComponent,
                canActivate: [AuthGuard],
                data: { roles: RoutesRestriction.LOGS }
            }
        ]
    },

    // Compute paths
    {
        path: 'compute', component: MainResourcesComputeComponent,
        children: [
            {
                path: '', redirectTo: 'endpoints', pathMatch: 'full'
            },
            {
                path: 'endpoints', component: FormerPlaceholderViewComponent
            },
            {
                path: 'compute', component: FormerPlaceholderViewComponent
            },
            {
                path: 'profiles', component: FormerPlaceholderViewComponent
            },
            {
                path: 'instance-types', component: InstanceTypesComponent,
                children: [{
                  path: 'new',
                  component: FormerPlaceholderViewComponent
                }, {
                  path: ':id',
                  component: FormerPlaceholderViewComponent
                }]
            },
            {
                path: 'placements', component: FormerPlaceholderViewComponent
            },
            {
                path: 'machines', component: FormerPlaceholderViewComponent
            },
            {
                path: 'networks', component: FormerPlaceholderViewComponent
            },
            {
                 path: '**', component: FormerPlaceholderViewComponent
            }
        ]
    }
];

export const ROUTING: ModuleWithProviders = RouterModule.forRoot(ROUTES, {useHash: true});
