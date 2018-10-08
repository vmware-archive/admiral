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

import { AppComponent } from './app.component';
import { TranslatePipe } from './utils/i18n.pipe';
import { ArrayElementsCountPipe } from './utils/count.pipe';
import { ProjectNamePipe } from './utils/project-name.pipe';
import { ProjectMembersCountPipe } from './utils/members-count.pipe';
import { MicroTimePipe } from './utils/microtime-pipe';
import { SeverityTextPipe } from './utils/severity-text.pipe';
import { LocaleDatePipe } from './utils/locale-date.pipe';
import { BreakOutModalDirective } from "./directives/shared/break-out-modal.directive";

import { AdministrationComponent } from './views/administration/administration.component';
import { MainResourcesComponent } from './views/main-resources/main-resources.component';

import { IdentityManagementComponent } from './views/identity-management/identity-management.component';
import { UsersGroupsComponent } from "./views/identity-management/users-groups.component";
import { UsersGroupsAssignRolesComponent } from "./views/identity-management/users-groups-assign-roles.component";

import { ProjectsComponent } from './views/projects/projects.component';
import { ProjectDetailsComponent } from './views/projects/project-details/project-details.component';
import { ProjectSummaryComponent } from "./views/projects/project-details/project-summary.component";
import { ProjectEditComponent } from "./views/projects/project-details/project-edit.component";
import { ProjectConfigurationComponent } from './views/projects/project-details/project-configuration.component';
import { ProjectCreateComponent } from './views/projects/project-create/project-create.component';
import { ProjectMembersComponent } from "./views/projects/project-details/project-members.component";
import { ProjectRegistriesComponent } from './views/projects/project-details/project-registries.component';
import { ProjectRegistryDetailsComponent } from './views/projects/project-details/project-registry-details.component';
import { ProjectAddMemberComponent } from "./views/projects/project-details/project-add-member.component";
import { ProjectAddMembersComponent } from "./views/projects/project-details/project-add-members.component";
import { ProjectEditMemberComponent } from "./views/projects/project-details/project-edit-member.component";
import { DeleteConfirmationComponent } from './views/delete-confirmation/delete-confirmation.component';
import { RegistriesComponent } from './views/registries/registries.component';
import { ConfigurationComponent } from './views/configuration/configuration.component';
import { LogsComponent } from './views/logs/logs.component';
import { SystemLogsComponent } from './views/logs/system-logs.component';
import { EndpointsComponent } from './views/endpoints/endpoints.component';
import { EndpointCreateComponent } from './views/endpoints/endpoint-create.component';
import { EndpointDetailsComponent } from './views/endpoints/endpoint-details.component';
import { RequestsComponent } from "./views/requests/requests.component";
import { EventLogsComponent } from "./views/event-logs/event-logs.component";
import { EndpointAssignmentsComponent } from "./views/endpoints/endpoint-assignments.component";

import { DashboardComponent } from './views/dashboard/dashboard.component';
import { FormerViewComponent, FormerPlaceholderViewComponent } from './views/former-view/former-view.component';
import { RepositoriesComponent } from './views/hbr/repository/repositories.component';
import { SingleRepositoryComponent } from './views/hbr/repository/single-repository.component';
import { ClustersComponent } from './views/clusters/clusters.component';
import { ClusterDetailsComponent } from './views/clusters/cluster-details/cluster-details.component';
import { ClusterSummaryComponent } from './views/clusters/cluster-details/cluster-summary.component';
import { ClusterResourcesComponent } from './views/clusters/cluster-details/cluster-resources.component';
import { ClusterAddHostComponent } from './views/clusters/cluster-details/cluster-add-host.component';
import { ClusterEditHostComponent } from './views/clusters/cluster-details/cluster-edit-host.component';
import { ClusterCreateComponent } from './views/clusters/cluster-create/cluster-create.component';
import { ClusterEditComponent } from './views/clusters/cluster-edit/cluster-edit.component';

import { VerifyCertificateComponent } from "./views/verify-certificate/verify-certificate.component";
import { TagDetailsContainersComponent } from "./views/tag-details/tag-details-containers.component";

import { KubernetesClustersComponent } from './views/kubernetes/clusters/kubernetes-clusters.component';
import { KubernetesClusterNewComponent } from './views/kubernetes/clusters/cluster-new/kubernetes-cluster-new.component';
import { KubernetesClusterNewSettingsComponent } from './views/kubernetes/clusters/cluster-new/kubernetes-cluster-new-settings.component';
import { KubernetesClusterDetailsComponent } from './views/kubernetes/clusters/cluster-details/kubernetes-cluster-details.component';
import { KubernetesClusterEditComponent } from './views/kubernetes/clusters/cluster-edit/kubernetes-cluster-edit.component';
import { KubernetesClusterSummaryComponent } from './views/kubernetes/clusters/cluster-details/kubernetes-cluster-summary.component';
import { KubernetesClusterNodesComponent } from './views/kubernetes/clusters/cluster-details/kubernetes-cluster-nodes.component';
import { KubernetesClusterAddComponent } from './views/kubernetes/clusters/cluster-add/kubernetes-cluster-add.component';
import { KubernetesClusterAddExistingComponent } from './views/kubernetes/clusters/cluster-add/kubernetes-cluster-add-existing.component';
import { KubernetesClusterAddExternalComponent } from './views/kubernetes/clusters/cluster-add-external/kubernetes-cluster-add-external.component';
import { KubernetesClusterEditExternalComponent } from "./views/kubernetes/clusters/cluster-edit/kubernetes-cluster-edit-external.component";

import { GridViewComponent } from './components/grid-view/grid-view.component';
import { TableViewComponent } from "./views/table-view/table-view.component";
import { CardComponent } from './components/card/card.component';
import { StatsComponent } from "./components/stats/stats.component";
import { LogsScrollComponent } from "./components/logs/logs-scroll.component";
import { MaximizableBehaviourComponent } from "./components/maximizable-behaviour/maximizable-behaviour.component";
import { BackButtonComponent } from "./components/back-button/back-button.component";
import { SimpleSearchComponent } from './components/search/simple-search.component';
import { DropdownComponent } from './components/dropdown/dropdown.component';
import { MultiCheckboxSelectorComponent } from "./components/multi-checkbox-selector/multi-checkbox-selector.component";
import { NavigationContainerComponent } from "./components/navigation-container/navigation-container.component";
import { LoginComponent } from './components/login/login.component';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';
import { TagDetailsComponent } from './views/tag-details/tag-details.component';
import { AllowNavigationDirective } from './directives/shared/allow-navigation.directive';

import { DummyComponent } from './components/dummy/dummy.component';

export const ADMIRAL_DECLARATIONS = [
  AppComponent,
  TranslatePipe,
  ArrayElementsCountPipe,
  ProjectNamePipe,
  ProjectMembersCountPipe,
  MicroTimePipe,
  LocaleDatePipe,
  SeverityTextPipe,
  BreakOutModalDirective,
  AllowNavigationDirective,

  AdministrationComponent,
  MainResourcesComponent,

  DeleteConfirmationComponent,
  SimpleSearchComponent,
  VerifyCertificateComponent,

  IdentityManagementComponent,
  UsersGroupsComponent,
  UsersGroupsAssignRolesComponent,
  ProjectsComponent,
  ProjectDetailsComponent,
  ProjectSummaryComponent,
  ProjectMembersComponent,
  ProjectAddMemberComponent,
  ProjectAddMembersComponent,
  ProjectEditMemberComponent,
  ProjectEditComponent,
  ProjectCreateComponent,
  ProjectConfigurationComponent,
  ProjectRegistriesComponent,
  ProjectRegistryDetailsComponent,
  RegistriesComponent,
  ConfigurationComponent,
  LogsComponent,
  SystemLogsComponent,
  TagDetailsContainersComponent,

  DashboardComponent,
  FormerViewComponent,
  FormerPlaceholderViewComponent,
  RepositoriesComponent,
  SingleRepositoryComponent,
  ClustersComponent,
  ClusterDetailsComponent,
  ClusterSummaryComponent,
  ClusterResourcesComponent,
  ClusterAddHostComponent,
  ClusterEditHostComponent,
  ClusterCreateComponent,
  ClusterEditComponent,
  EndpointsComponent,
  EndpointCreateComponent,
  EndpointDetailsComponent,
  EndpointAssignmentsComponent,
  KubernetesClustersComponent,
  KubernetesClusterNewComponent,
  KubernetesClusterNewSettingsComponent,
  KubernetesClusterEditComponent,
  KubernetesClusterDetailsComponent,
  KubernetesClusterSummaryComponent,
  KubernetesClusterNodesComponent,
  KubernetesClusterAddComponent,
  KubernetesClusterAddExistingComponent,
  KubernetesClusterAddExternalComponent,
  KubernetesClusterEditExternalComponent,
  RequestsComponent,
  EventLogsComponent,

  GridViewComponent,
  TableViewComponent,
  CardComponent,
  StatsComponent,
  LogsScrollComponent,
  MaximizableBehaviourComponent,
  BackButtonComponent,
  DropdownComponent,
  MultiCheckboxSelectorComponent,
  NavigationContainerComponent,
  LoginComponent,

  PodListComponent,
  PodDetailsComponent,
  DeploymentListComponent,
  DeploymentDetailsComponent,
  ServiceListComponent,
  ServiceDetailsComponent,
  TagDetailsComponent,

  DummyComponent
];
