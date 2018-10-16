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

import { Roles } from './roles';

/**
 * Restrictions on routing based on user roles.
 */
export class RoutesRestriction {

  public static HOME = [Roles.CLOUD_ADMIN, Roles.BASIC_USER, Roles.PROJECT_ADMIN,
                        Roles.PROJECT_MEMBER, Roles.PROJECT_VIEWER];
  public static ADMINISTRATION = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static IDENTITY_MANAGEMENT = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static PROJECTS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECTS_NEW = [Roles.CLOUD_ADMIN];

  public static DEPLOYMENTS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER,
                               Roles.VRA_CONTAINER_ADMIN];
  public static TEMPLATES = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER,
                             Roles.VRA_CONTAINER_ADMIN, Roles.VRA_CONTAINER_DEVELOPER];
  public static TEMPLATES_NEW_IMPORT = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN,
                                        Roles.PROJECT_MEMBER, Roles.VRA_CONTAINER_ADMIN];

  public static PUBLIC_REPOSITORIES = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN,
                                       Roles.PROJECT_MEMBER, Roles.VRA_CONTAINER_ADMIN,
                                       Roles.VRA_CONTAINER_DEVELOPER];

  public static PROJECTS_ID = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECTS_ID_EDIT = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECTS_ID_ADD_MEMBER = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static REGISTRIES = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static CONFIGURATION = [Roles.CLOUD_ADMIN];
  public static LOGS = [Roles.CLOUD_ADMIN];

  public static PROJECT_CARD_VIEW_ACTIONS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECT_CARD_VIEW_REMOVE_ACTION = [Roles.CLOUD_ADMIN];

  public static PROJECT_SUMMARY_EDIT = [Roles.CLOUD_ADMIN];
  public static PROJECT_MEMBERS_ADD = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECT_MEMBER_ACTIONS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECT_REGISTRY_REPLICATION = [Roles.CLOUD_ADMIN];
  public static PROJECT_REGISTRIES = [Roles.PROJECT_ADMIN];
  public static PROJECT_REGISTRIES_DETAILS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];

  public static CLUSTERS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER,
                            Roles.VRA_CONTAINER_ADMIN];
  public static CLUSTERS_NEW = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static CLUSTERS_ID = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static CLUSTERS_EDIT = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];

  public static KUBERNETES_CLUSTERS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN,
                                       Roles.PROJECT_MEMBER,Roles.VRA_CONTAINER_ADMIN,
                                       Roles.VRA_CONTAINER_DEVELOPER];
  public static KUBERNETES_CLUSTERS_NEW = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN,
                                           Roles.VRA_CONTAINER_DEVELOPER, Roles.PROJECT_MEMBER];
  public static KUBERNETES_CLUSTERS_ADD = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static KUBERNETES_CLUSTERS_ID = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN,
                                          Roles.VRA_CONTAINER_DEVELOPER];
  public static KUBERNETES_CLUSTERS_EDIT = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];

  public static REQUESTS_DELETE = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static EVENT_LOGS_DELETE = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];

  public static ENDPOINTS_MENU_VRA = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static ENDPOINTS_NEW_VRA = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static ENDPOINTS_REMOVE_VRA = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];

  public static KUBERNETES_MENU = [Roles.VRA_CONTAINER_DEVELOPER];

  public static INFRASTRUCTURE = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER,
                                  Roles.VRA_CONTAINER_ADMIN, Roles.VRA_CONTAINER_ADMIN,
                                  Roles.VRA_CONTAINER_DEVELOPER];

  public static FAVORITE_IMAGES = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static DEPLOYMENT_POLICIES = [Roles.CLOUD_ADMIN, Roles.VRA_CONTAINER_ADMIN];
  public static PROVISIONING_ADDITIONAL_INFO_BUTTON = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN,
                                                       Roles.PROJECT_MEMBER,
                                                       Roles.VRA_CONTAINER_ADMIN];
  public static ACTIVITIES = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER,
                              Roles.VRA_CONTAINER_ADMIN,
                              Roles.VRA_CONTAINER_DEVELOPER];
}
