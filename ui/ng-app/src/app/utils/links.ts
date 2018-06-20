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

export class Links {

  public static HOME = '/home';
  public static RESOURCES = '/resources';
  public static UTIL = '/util';
  public static CONTAINER_HOSTS = Links.RESOURCES + '/hosts';
  public static PODS = Links.RESOURCES + '/kubernetes-pods';
  public static DEPLOYMENTS = Links.RESOURCES + '/kubernetes-deployments';
  public static SERVICES = Links.RESOURCES + '/kubernetes-services';
  public static REPLICATION_CONTROLLERS = Links.RESOURCES + '/kubernetes-replication-controllers';
  public static CONTAINER_LOGS = Links.RESOURCES + '/container-logs';
  public static POD_LOGS = Links.RESOURCES + '/kubernetes-pod-logs';
  public static CLUSTERS = Links.RESOURCES + '/clusters';
  public static CONTAINERS = Links.RESOURCES + '/containers';
  public static CONTAINER_DESCRIPTIONS = Links.RESOURCES + '/container-descriptions';
  public static GROUPS = '/groups';
  public static PROJECTS = '/projects';
  public static EVENT_LOGS = Links.RESOURCES + '/event-logs';
  public static PKS_ENDPOINTS = Links.RESOURCES + '/pks/endpoints';
  public static PKS_ENDPOINT_CREATE = Links.RESOURCES + '/pks/create-endpoint';
  public static PKS_ENDPOINT_TEST_CONNECTION = Links.RESOURCES + '/pks/create-endpoint?validate';
  public static PKS_CLUSTERS = Links.RESOURCES + '/pks/clusters';
  public static PKS_CLUSTERS_ADD = Links.RESOURCES + '/pks/clusters-config';
  public static KUBE_CONFIG_CONTENT = Links.RESOURCES + '/kube-config';
  public static DEPLOYMENT_POLICIES = Links.RESOURCES + '/deployment-policies';

  public static COMPOSITE_DESCRIPTIONS = Links.RESOURCES + '/composite-descriptions';
  public static COMPOSITE_DESCRIPTIONS_CLONE = Links.RESOURCES + '/composite-descriptions-clone';
  public static COMPOSITE_DESCRIPTIONS_CONTENT = Links.RESOURCES + '/composite-templates';
  public static COMPOSITE_COMPONENTS = Links.RESOURCES + '/composite-components';

  public static CONFIG = '/config';
  public static CONFIG_PROPS = Links.CONFIG + '/props';
  public static INSTANCE_TYPES = Links.CONFIG + '/instance-types';
  public static REGISTRIES = Links.CONFIG + '/registries';
  public static REGISTRY_SPEC = Links.CONFIG + '/registry-spec';
  public static SSL_TRUST_CERTS_IMPORT = Links.CONFIG + '/trust-certs-import';

  public static AUTH = '/auth';
  public static USER_SESSION = Links.AUTH + '/session';
  public static AUTH_IDM = Links.AUTH + '/idm';
  public static AUTH_PRINCIPALS = Links.AUTH_IDM + '/principals';
  public static AUTH_LOGOUT = Links.USER_SESSION + '/logout';
  public static BASIC_AUTH = '/core/authn/basic';

  public static CREDENTIALS = '/core/auth/credentials';

  public static REQUESTS = '/requests';

  public static HOST_DATA_COLLECTION =
                            Links.RESOURCES + '/hosts-data-collections/host-info-data-collection';

  public static LONG_URI_GET = Links.UTIL + '/long-uri-get';

}
