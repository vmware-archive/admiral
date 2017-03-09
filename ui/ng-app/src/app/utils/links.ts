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

export class Links {
  public static RESOURCES = '/resources';
  public static PODS = Links.RESOURCES + '/kubernetes-pods';
  public static DEPLOYMENTS = Links.RESOURCES + '/kubernetes-deployments';
  public static SERVICES = Links.RESOURCES + '/kubernetes-services';
  public static REPLICATION_CONTROLLERS = Links.RESOURCES + '/kubernetes-replication-controllers';
  public static CONTAINER_LOGS = Links.RESOURCES + '/container-logs';
  public static POD_LOGS = Links.RESOURCES + '/kubernetes-pods-logs';
}