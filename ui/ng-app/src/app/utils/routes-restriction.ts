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

import { Roles } from './roles';

export class RoutesRestriction {

  public static ADMINISTRATION = [Roles.CLOUD_ADMIN];
  public static IDENTITY_MANAGEMENT = [Roles.CLOUD_ADMIN];
  public static PROJECTS = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER, Roles.PROJECT_VIEWER];
  public static PROJECTS_NEW = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECTS_ID = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN, Roles.PROJECT_MEMBER, Roles.PROJECT_VIEWER];
  public static PROJECTS_ID_EDIT = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static PROJECTS_ID_ADD_MEMBER = [Roles.CLOUD_ADMIN, Roles.PROJECT_ADMIN];
  public static REGISTRIES = [Roles.CLOUD_ADMIN];
  public static CONFIGURATION = [Roles.CLOUD_ADMIN];
  public static LOGS = [Roles.CLOUD_ADMIN];
}