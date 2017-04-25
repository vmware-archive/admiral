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
import { BreakOutModalDirective } from "./directives/shared/break-out-modal.directive";

import { AdministrationComponent } from './views/administration/administration.component';
import { MainResourcesComponent } from './views/main-resources/main-resources.component';

import { IdentityManagementComponent } from './views/identity-management/identity-management.component';
import { ProjectsComponent } from './views/projects/projects.component';
import { ProjectDetailsComponent } from './views/projects/project-details/project-details.component';
import { ProjectCreateComponent } from './views/projects/project-create/project-create.component';
import { RegistriesComponent } from './views/registries/registries.component';
import { ConfigurationComponent } from './views/configuration/configuration.component';
import { LogsComponent } from './views/logs/logs.component';

import { DashboardComponent } from './views/dashboard/dashboard.component';
import { FormerViewComponent } from './views/former-view/former-view.component';
import { ProjectRepositoriesComponent } from './views/project-repositories/project-repositories.component';
import { PublicRepositoriesComponent } from './views/public-repositories/public-repositories.component';
import { VchClustersComponent } from './views/vch-clusters/vch-clusters.component';

import { GridViewComponent } from './components/grid-view/grid-view.component';
import { CardComponent } from './components/card/card.component';
import { StatsComponent } from "./components/stats/stats.component";
import { LogsScrollComponent } from "./components/logs/logs-scroll.component";
import { MaximizableBehaviourComponent } from "./components/maximizable-behaviour/maximizable-behaviour.component";
import { BackButtonComponent } from "./components/back-button/back-button.component";
import { SearchComponent } from "./components/search/search.component";
import { NavigationContainerComponent } from "./components/navigation-container/navigation-container.component";
import { NetworkBoxComponent } from "./components/network-box/network-box.component";
import { VolumeBoxComponent } from "./components/volume-box/volume-box.component";

import { PodListComponent } from './kubernetes/pods/list/pod-list.component';
import { PodDetailsComponent } from './kubernetes/pods/details/pod-details.component';
import { PodDetailsPropertiesComponent } from './kubernetes/pods/details/pod-details-properties.component';
import { DeploymentListComponent } from './kubernetes/deployments/list/deployment-list.component';
import { DeploymentDetailsComponent } from './kubernetes/deployments/details/deployment-details.component';
import { ServiceListComponent } from './kubernetes/services/list/service-list.component';
import { ServiceDetailsComponent } from './kubernetes/services/details/service-details.component';
import { GenericDefinitionFormComponent } from './kubernetes/generic-definition-form/generic-definition-form.component';
import { GenericTemplateItemComponent } from './kubernetes/generic-template-item/generic-template-item.component';


export const ADMIRAL_DECLARATIONS = [
  AppComponent,
  TranslatePipe,
  BreakOutModalDirective,

  AdministrationComponent,
  MainResourcesComponent,

  IdentityManagementComponent,
  ProjectsComponent,
  ProjectDetailsComponent,
  ProjectCreateComponent,
  RegistriesComponent,
  ConfigurationComponent,
  LogsComponent,

  DashboardComponent,
  FormerViewComponent,
  ProjectRepositoriesComponent,
  PublicRepositoriesComponent,
  VchClustersComponent,

  GridViewComponent,
  CardComponent,
  StatsComponent,
  LogsScrollComponent,
  MaximizableBehaviourComponent,
  BackButtonComponent,
  SearchComponent,
  NavigationContainerComponent,
  NetworkBoxComponent,
  VolumeBoxComponent,

  PodListComponent,
  PodDetailsComponent,
  PodDetailsPropertiesComponent,
  DeploymentListComponent,
  DeploymentDetailsComponent,
  ServiceListComponent,
  ServiceDetailsComponent,
  GenericDefinitionFormComponent,
  GenericTemplateItemComponent
];
