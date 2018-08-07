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

import { ModuleWithProviders } from '@angular/core/src/metadata/ng_module';
import { Routes, RouterModule } from '@angular/router';
import { RoutesRestriction } from './utils/routes-restriction';

import { AdministrationComponent } from './views/administration/administration.component';
import { MainResourcesComponent } from './views/main-resources/main-resources.component';

import { IdentityManagementComponent } from './views/identity-management/identity-management.component';
import { ProjectsComponent } from './views/projects/projects.component';
import { ProjectDetailsComponent } from './views/projects/project-details/project-details.component';
import { ProjectCreateComponent } from './views/projects/project-create/project-create.component';
import { ProjectAddMemberComponent } from "./views/projects/project-details/project-add-member.component";
import { ProjectRegistryDetailsComponent } from './views/projects/project-details/project-registry-details.component';
import { RegistriesComponent } from './views/registries/registries.component';
import { ConfigurationComponent } from './views/configuration/configuration.component';
import { LogsComponent } from './views/logs/logs.component';
import { EndpointsComponent } from './views/endpoints/endpoints.component';
import { EndpointDetailsComponent } from './views/endpoints/endpoint-details.component';

import { DashboardComponent } from './views/dashboard/dashboard.component';
import { FormerPlaceholderViewComponent } from './views/former-view/former-view.component';
import { RepositoriesComponent } from './views/hbr/repository/repositories.component';
import { SingleRepositoryComponent } from './views/hbr/repository/single-repository.component';
import { ClustersComponent } from './views/clusters/clusters.component';
import { ClusterDetailsComponent } from './views/clusters/cluster-details/cluster-details.component';
import { ClusterCreateComponent } from './views/clusters/cluster-create/cluster-create.component';
import { ClusterEditComponent } from './views/clusters/cluster-edit/cluster-edit.component';

import { KubernetesClustersComponent } from './views/kubernetes/clusters/kubernetes-clusters.component';
import { KubernetesClusterNewComponent } from './views/kubernetes/clusters/cluster-new/kubernetes-cluster-new.component';
import { KubernetesClusterEditComponent } from './views/kubernetes/clusters/cluster-edit/kubernetes-cluster-edit.component';
import { KubernetesClusterAddComponent } from './views/kubernetes/clusters/cluster-add/kubernetes-cluster-add.component';
import { KubernetesClusterDetailsComponent } from './views/kubernetes/clusters/cluster-details/kubernetes-cluster-details.component';
import { KubernetesClusterEditExternalComponent } from "./views/kubernetes/clusters/cluster-edit/kubernetes-cluster-edit-external.component";

import { DummyComponent } from './components/dummy/dummy.component';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';

import { NavigationContainerType } from './components/navigation-container/navigation-container.component';

import { AdminAuthGuard } from 'app/services/admin-auth-guard.service';
import { HomeAuthGuard } from 'app/services/home-auth-guard.service';
import { TagDetailsComponent } from './views/tag-details/tag-details.component';
import { RequestsComponent } from "./views/requests/requests.component";
import { EventLogsComponent } from "./views/event-logs/event-logs.component";

export const ROUTES: Routes = [
    {
        path: '', redirectTo: 'home', pathMatch: 'full'
    },
    {
        path: 'home', component: MainResourcesComponent,
        children: [
            {
                path: '', canActivate: [HomeAuthGuard],
                    data: { roles: RoutesRestriction.HOME }, pathMatch: 'full',
                    redirectTo: 'applications'
            },
            {
                path: 'dashboard', component: DashboardComponent,
            },
            {
                path: 'applications', component: DummyComponent, canActivate: [HomeAuthGuard],
                    data: { roles: RoutesRestriction.DEPLOYMENTS }
            },
            {
                path: 'containers', component: DummyComponent, canActivate: [HomeAuthGuard],
                    data: { roles: RoutesRestriction.DEPLOYMENTS }
            },
            {
                path: 'networks', component: DummyComponent, canActivate: [HomeAuthGuard],
                    data: { roles: RoutesRestriction.DEPLOYMENTS }
            },
            {
                path: 'volumes', component: DummyComponent, canActivate: [HomeAuthGuard],
                    data: { roles: RoutesRestriction.DEPLOYMENTS }
            },
            {
                path: 'project-repositories', component: RepositoriesComponent
            },
            {
                path: 'project-repositories/repositories/:rid', component: SingleRepositoryComponent
            },
            {
                path: 'project-repositories/repositories/:rid/tags/:tid', component: TagDetailsComponent
            },
            {
                path: 'registries', component: RegistriesComponent
            },
            {
                path: 'identity-management', component: IdentityManagementComponent
            },
            {
                path: 'clusters', component: ClustersComponent,
                data: { roles: RoutesRestriction.CLUSTERS },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'clusters/cluster/new', component: ClusterCreateComponent,
                data: { roles: RoutesRestriction.CLUSTERS_NEW },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'clusters/cluster/:id', component: ClusterDetailsComponent,
                data: { roles: RoutesRestriction.CLUSTERS_ID },
                canActivate: [HomeAuthGuard]
            },
            { path: 'clusters/cluster/:id/edit', component: ClusterEditComponent,
                data: { roles: RoutesRestriction.CLUSTERS_EDIT },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'kubernetes/clusters', component: KubernetesClustersComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'kubernetes/new', component: KubernetesClusterNewComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS_NEW },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'kubernetes/add', component: KubernetesClusterAddComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS_ADD },
                canActivate: [HomeAuthGuard]
            },
            {
                path: 'kubernetes/clusters/cluster/:id',
                canActivate: [HomeAuthGuard], component: KubernetesClusterDetailsComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS_ID }
            },
            {
                path: 'kubernetes/clusters/cluster/:id/edit',
                canActivate: [HomeAuthGuard], component: KubernetesClusterEditComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS_EDIT }
            },
            {
                path: 'kubernetes/clusters/cluster/:id/edit-external',
                canActivate: [HomeAuthGuard], component: KubernetesClusterEditExternalComponent,
                data: { roles: RoutesRestriction.KUBERNETES_CLUSTERS_EDIT }
            },
            {
                path: 'kubernetes/pods', component: PodListComponent
            },
            {
                path: 'kubernetes/pods/:id', component: PodDetailsComponent
            },
            {
                path: 'kubernetes/deployments', component: DeploymentListComponent
            },
            {
                path: 'kubernetes/deployments/:id', component: DeploymentDetailsComponent
            },
            /* k8s services are out of the current scope.
            {
                path: 'kubernetes/services', component: ServiceListComponent
            },
            {
                path: 'kubernetes/services/:id', component: ServiceDetailsComponent
            },
            */
            {
                path: 'endpoints', component: EndpointsComponent
            },
            {
                path: 'endpoints/new', component: EndpointDetailsComponent
            },
            {
                path: 'endpoints/:id', component: EndpointDetailsComponent
            },
            {
                path: 'requests', component: RequestsComponent
            },
            {
                path: 'event-logs', component: EventLogsComponent
            },
            {
                path: 'event-logs/:id', component: EventLogsComponent
            },
            {
                 path: '**', component: FormerPlaceholderViewComponent
            }
        ]
    },
    {
        path: 'administration', component: AdministrationComponent,
        canActivate: [AdminAuthGuard],
        data: { roles: RoutesRestriction.ADMINISTRATION },
        children: [
            {
                path: '', redirectTo: 'projects', pathMatch: 'full'
            },
            {
                path: 'identity-management', component: IdentityManagementComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.IDENTITY_MANAGEMENT }
            },
            {
                path: 'projects', component: ProjectsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECTS },
                children: [
                    { path: 'new', component: ProjectCreateComponent,
                        canActivate: [AdminAuthGuard],
                        data: {
                            navigationContainerType: NavigationContainerType.Default,
                            roles: RoutesRestriction.PROJECTS_NEW
                        }
                    }
                ]
            },
            { path: 'projects/:id', component: ProjectDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECTS_ID }
            },
            { path: 'projects/:id/edit', component: ProjectCreateComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECTS_ID_EDIT }
            },
            { path: 'projects/:id/:tab', component: ProjectDetailsComponent,
                data: { roles: RoutesRestriction.PROJECTS_ID }
            },
            { path: 'projects/:id/:tab/edit', component: ProjectCreateComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECTS_ID_EDIT }
            },
            { path: 'projects/:projectId/cluster/new', component: ClusterCreateComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_NEW }
            },
            { path: 'projects/:projectId/:tab/cluster/new', component: ClusterCreateComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_NEW }
            },
            { path: 'projects/:projectId/cluster/:id', component: ClusterDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_ID }
            },
            { path: 'projects/:projectId/:tab/cluster/:id', component: ClusterDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_ID }
            },
            { path: 'projects/:projectId/cluster/:id/edit', component: ClusterEditComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_EDIT }
            },
            { path: 'projects/:projectId/:tab/cluster/:id/edit', component: ClusterEditComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CLUSTERS_EDIT }
            },
            { path: 'projects/:projectId/registries/new', component: ProjectRegistryDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECT_REGISTRIES_DETAILS }
            },
            { path: 'projects/:projectId/:tab/registries/new', component: ProjectRegistryDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECT_REGISTRIES_DETAILS }
            },
            { path: 'projects/:projectId/registries/registry/:id/edit', component: ProjectRegistryDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECT_REGISTRIES_DETAILS }
            },
            { path: 'projects/:projectId/:tab/registries/registry/:id/edit', component: ProjectRegistryDetailsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECT_REGISTRIES_DETAILS }
            },
            { path: 'projects/:id/repositories/:rid', component: SingleRepositoryComponent
            },
            { path: 'projects/:id/:tab/repositories/:rid', component: SingleRepositoryComponent
            },
            { path: 'projects/:id/repositories/:rid/tags/:tid', component: TagDetailsComponent
            },
            { path: 'projects/:id/:tab/repositories/:rid/tags/:tid', component: TagDetailsComponent
            },
            { path: 'projects/:id/add-member', component: ProjectAddMemberComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.PROJECTS_ID_ADD_MEMBER }
            },
            {
                path: 'registries', component: RegistriesComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.REGISTRIES }
            },
            {
                path: 'registries/:tab', component: RegistriesComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.REGISTRIES }
            },
            {
                path: 'configuration', component: ConfigurationComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.CONFIGURATION }
            },
            {
                path: 'logs', component: LogsComponent,
                canActivate: [AdminAuthGuard],
                data: { roles: RoutesRestriction.LOGS }
            }
        ]
    }
];

export const ROUTING: ModuleWithProviders = RouterModule.forRoot(ROUTES, {useHash: true});
