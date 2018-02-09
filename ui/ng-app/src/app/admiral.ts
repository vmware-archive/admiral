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
import { ProjectConfigurationComponent } from './views/projects/project-details/project-configuration.component';
import { ProjectCreateComponent } from './views/projects/project-create/project-create.component';
import { ProjectMembersComponent } from "./views/projects/project-details/project-members.component";
import { ProjectAddMemberComponent } from "./views/projects/project-details/project-add-member.component";
import { ProjectAddMembersComponent } from "./views/projects/project-details/project-add-members.component";
import { ProjectEditMemberComponent } from "./views/projects/project-details/project-edit-member.component";
import { DeleteConfirmationComponent } from './views/delete-confirmation/delete-confirmation.component';
import { RegistriesComponent } from './views/registries/registries.component';
import { ConfigurationComponent } from './views/configuration/configuration.component';
import { LogsComponent } from './views/logs/logs.component';
import { SystemLogsComponent } from './views/logs/system-logs.component';

import { DashboardComponent } from './views/dashboard/dashboard.component';
import { FormerViewComponent, FormerPlaceholderViewComponent } from './views/former-view/former-view.component';
import { RepositoryComponent } from './views/hbr/repository/repository.component';
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

import { GridViewComponent } from './components/grid-view/grid-view.component';
import { CardComponent } from './components/card/card.component';
import { StatsComponent } from "./components/stats/stats.component";
import { LogsScrollComponent } from "./components/logs/logs-scroll.component";
import { MaximizableBehaviourComponent } from "./components/maximizable-behaviour/maximizable-behaviour.component";
import { BackButtonComponent } from "./components/back-button/back-button.component";
import { SearchComponent } from "./components/search/search.component";
import { SimpleSearchComponent } from './components/search/simple-search.component';
import { DropdownComponent } from './components/dropdown/dropdown.component';
import { NavigationContainerComponent } from "./components/navigation-container/navigation-container.component";
import { LoginComponent } from './components/login/login.component';

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { PodDetailsPropertiesComponent } from './kubernetes/pods/details/pod-details-properties.component';
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
  ProjectCreateComponent,
  ProjectConfigurationComponent,
  RegistriesComponent,
  ConfigurationComponent,
  LogsComponent,
  SystemLogsComponent,
  TagDetailsContainersComponent,

  DashboardComponent,
  FormerViewComponent,
  FormerPlaceholderViewComponent,
  RepositoryComponent,
  ClustersComponent,
  ClusterDetailsComponent,
  ClusterSummaryComponent,
  ClusterResourcesComponent,
  ClusterAddHostComponent,
  ClusterEditHostComponent,
  ClusterCreateComponent,
  ClusterEditComponent,

  GridViewComponent,
  CardComponent,
  StatsComponent,
  LogsScrollComponent,
  MaximizableBehaviourComponent,
  BackButtonComponent,
  SearchComponent,
  DropdownComponent,
  NavigationContainerComponent,
  LoginComponent,

  PodListComponent,
  PodDetailsComponent,
  PodDetailsPropertiesComponent,
  DeploymentListComponent,
  DeploymentDetailsComponent,
  ServiceListComponent,
  ServiceDetailsComponent,
  TagDetailsComponent,

  DummyComponent
];
