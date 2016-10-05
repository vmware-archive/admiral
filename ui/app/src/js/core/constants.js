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

var constants = Immutable({
  LOADING: {},
  CONTEXT_PANEL: {
    RESOURCE_POOLS: 'resourcePools',
    CREDENTIALS: 'credentials',
    CERTIFICATES: 'certificates',
    RESOURCE_GROUPS: 'resourceGroups',
    DEPLOYMENT_POLICIES: 'deploymentPolicies',
    REQUESTS: 'requests',
    EVENTLOGS: 'eventlogs',
    ENDPOINTS: 'endpoints'
  },
  VIEWS: {
    HOME: {name: 'home'},
    HOSTS: {
      name: 'hosts',
      NEW: {name: 'hosts/new'}
    },
    PLACEMENTS: {
      name: 'placements'
    },
    TEMPLATES: {
      name: 'templates',
      NEW: {name: 'templates/new'}
    },
    RESOURCES: {
      name: 'resources',
      VIEWS: {
        CONTAINERS: {
          name: 'containers'
        },
        APPLICATIONS: {
          name: 'applications'
        },
        NETWORKS: {
          name: 'networks'
        }
      }
    }
  },
  CREDENTIALS_TYPE: {
    PUBLIC_KEY: 'PublicKey',
    PRIVATE_KEY: 'PrivateKey',
    PASSWORD: 'Password'
  },
  CREDENTIALS_SCOPE_TYPE: {
    SYSTEM: 'SYSTEM'
  },
  VISUALS: {
    ITEM_HIGHLIGHT_ACTIVE_TIMEOUT: 1500
  },
  HOSTS: {
    OPERATION: {
      ENABLE: 'ENABLE',
      DISABLE: 'DISABLE',
      DATACOLLECT: 'DATACOLLECT'
    }
  },
  // TODO move all entries from CONTAINERS to RESOURCES.CONTAINER
  RESOURCES: {
    TYPES: {
      NETWORK: 'NETWORK'
    },
    SEARCH_CATEGORY: {
      ALL: 'all',
      CONTAINERS: 'containers',
      APPLICATIONS: 'applications',
      NETWORKS: 'networks'
    },
    NETWORKS: {
      OPERATION: {
        REMOVE: 'REMOVE'
      },
      STATES: {
        PROVISIONING: 'PROVISIONING',
        CONNECTED: 'CONNECTED',
        ERROR: 'ERROR',
        UNKNOWN: 'UNKNOWN',
        RETIRED: 'RETIRED'
      }
    }
  },
  CONTAINERS: {
    STATES: {
      PROVISIONING: 'PROVISIONING',
      RUNNING: 'RUNNING',
      REBOOTING: 'REBOOTING',
      STOPPED: 'STOPPED',
      PAUSED: 'PAUSED',
      ERROR: 'ERROR',
      UNKNOWN: 'UNKNOWN',
      RETIRED: 'RETIRED',
      PRO: 'RETIRED'
    },
    LOGS: {
      SINCE_DURATIONS: [
        moment.duration(15, 'minutes').asMilliseconds(),
        moment.duration(30, 'minutes').asMilliseconds(),
        moment.duration(1, 'hours').asMilliseconds(),
        moment.duration(2, 'hours').asMilliseconds(),
        moment.duration(5, 'hours').asMilliseconds()
      ]
    },
    TYPES: {
      SINGLE: 'SINGLE',
      COMPOSITE: 'COMPOSITE',
      CLUSTER: 'CLUSTER'
    },
    SEARCH_CATEGORY: {
      ALL: 'all',
      CONTAINERS: 'containers',
      APPLICATIONS: 'applications'
    },
    OPERATION: {
      LIST: 'LIST',
      DETAILS: 'DETAILS',
      LOGS: 'LOGS',
      STATS: 'STATS',
      CREATE: 'CREATE',
      START: 'START',
      STOP: 'STOP',
      REMOVE: 'REMOVE',
      SHELL: 'SHELL',
      CLUSTERING: 'CLUSTERING',
      DEFAULT: 'DEFAULT',
      NETWORKCREATE: 'NETWORKCREATE'
    }
  },
  TEMPLATES: {
    TYPES: {
      TEMPLATE: 'TEMPLATE',
      IMAGE: 'IMAGE'
    },
    SEARCH_CATEGORY: {
      ALL: 'all',
      IMAGES: 'images',
      TEMPLATES: 'templates'
    },
    STATUS: {
      PUBLISHED: 'PUBLISHED',
      DRAFT: 'DRAFT'
    },
    OPERATION: {
      PROVISION: 'PROVISION',
      IMPORT: 'IMPORT',
      EXPORT: 'EXPORT',
      PUBLISH: 'PUBLISH',
      REMOVE: 'REMOVE'
    },
    EXPORT_FORMAT: {
      COMPOSITE_BLUEPRINT: 'COMPOSITE_BLUEPRINT',
      DOCKER_COMPOSE: 'DOCKER_COMPOSE'
    }
  },
  REQUESTS: {
    STAGES: {
      CREATED: 'CREATED',
      STARTED: 'STARTED',
      FINISHED: 'FINISHED',
      FAILED: 'FAILED',
      CANCELLED: 'CANCELLED'
    },
    REFRESH_INTERVAL: 5000
  },
  EVENTLOG: {
    TYPE: {
      INFO: 'INFO',
      WARNING: 'WARNING',
      ERROR: 'ERROR'
    },
    REFRESH_INTERVAL: 5000
  },
  NOTIFICATIONS: {
    REFRESH_INTERVAL: 5000
  },
  SEARCH_CATEGORY_PARAM: '$category',
  SEARCH_OCCURRENCE: {
    PARAM: '$occurrence',
    ALL: 'all',
    ANY: 'any'
    /* Not supported by DCP
    NONE: 'none'
    */
  },
  ALERTS: {
    TYPE: {
      FAIL: 'danger',
      WARNING: 'warning',
      SUCCESS: 'success'
    }
  },
  PROPERTIES: {
    VISIBILITY_HIDDEN_PREFIX: '__'
  },
  ERRORS: {
    NOT_FOUND: 404
  },
  STATES: {
    ON: 'ON',
    OFF: 'OFF',
    SUSPEND: 'SUSPEND',
    UNKNOWN: 'UNKNOWN'
  },
  OPERATIONS: {
    PENDING_COUNTDOWN_SECONDS: 3
  },
  NETWORK_MODES: {
    NONE: 'NONE',
    BRIDGE: 'BRIDGE',
    HOST: 'HOST'
  },
  NEW_ITEM_SYSTEM_VALUE: '__new'
});

export default constants;
