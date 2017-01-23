/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

const CONFIG = '/config';
const RESOURCES = '/resources';

var links = Immutable({
  CONFIG: '/config',
  RESOURCES: '/resources',
  REGISTRIES: CONFIG + '/registries',
  REGISTRY_HOSTS: CONFIG + '/registry-spec',
  ENVIRONMENTS: CONFIG + '/environments',
  ENDPOINTS: CONFIG + '/endpoints',
  COMPUTE_PROFILES: CONFIG + '/compute-profiles',
  NETWORK_PROFILES: CONFIG + '/network-profiles',
  STORAGE_PROFILES: CONFIG + '/storage-profiles',

  COMPUTE_DESCRIPTIONS: RESOURCES + '/compute-descriptions',
  COMPUTE_RESOURCES: RESOURCES + '/compute',
  CONTAINER_SHELL: RESOURCES + '/container-shell',
  CREDENTIALS: '/core/auth/credentials',
  PLACEMENT_ZONES: RESOURCES + '/pools',
  RESOURCE_GROUP_PLACEMENTS: RESOURCES + '/group-placements',
  EVENT_LOGS: RESOURCES + '/event-logs',
  NOTIFICATIONS: RESOURCES + '/notifications',
  DEPLOYMENT_POLICIES: RESOURCES + '/deployment-policies',
  TAGS: RESOURCES + '/tags',
  EPZ_CONFIG: RESOURCES + '/elastic-placement-zones-config',

  CONTAINER_HOSTS: RESOURCES + '/hosts',
  CONTAINERS: RESOURCES + '/containers',
  CONTAINER_LOGS: RESOURCES + '/container-logs',
  CONTAINER_DESCRIPTIONS: RESOURCES + '/container-descriptions',
  CONTAINER_NETWORK_DESCRIPTIONS: RESOURCES + '/container-network-descriptions',
  CONTAINER_VOLUMES_DESCRIPTIONS: RESOURCES + '/container-volume-descriptions',
  COMPOSITE_DESCRIPTIONS: RESOURCES + '/composite-descriptions',
  COMPOSITE_DESCRIPTIONS_CLONE: RESOURCES + '/composite-descriptions-clone',
  COMPOSITE_DESCRIPTIONS_CONTENT: RESOURCES + '/composite-templates',
  COMPOSITE_COMPONENTS: RESOURCES + '/composite-components',
  CONTAINER_VOLUMES: RESOURCES + '/container-volumes',
  MANAGE_CONTAINERS_ENDPOINT: '/manage',
  DATA_COLLECTION: '/data-collection/types/App.Container',

  NETWORKS: RESOURCES + '/container-networks',
  VOLUMES: RESOURCES + '/container-volumes',

  SSL_TRUST_CERTS: CONFIG + '/trust-certs',
  SSL_TRUST_CERTS_IMPORT: CONFIG + '/trust-certs-import',
  REQUESTS: '/requests',
  REQUEST_STATUS: '/request-status',

  IMAGES: '/images',
  IMAGE_TAGS: '/images/tags',
  TEMPLATES: '/templates',
  POPULAR_IMAGES: '/popular-images',

  GROUPS: '/groups',
  RESOURCE_GROUPS: RESOURCES + '/groups',

  BASIC_AUTH: '/core/authn/basic',
  USER_SESSION: '/user-session',
  CONFIG_PROPS: CONFIG + '/props',

  DELETE_TASKS: '/delete-tasks',

  CLOSURE_DESCRIPTIONS: RESOURCES + '/closure-descriptions',
  CLOSURES: RESOURCES + '/closures',

  // Explicitly relative for both standalone and CAFE
  CONTAINER_IMAGE_ICONS: 'container-image-icons',

  // Artificial internal only link
  SYSTEM_NETWORK_LINK: '/system-networks-link'
});

export default links;
