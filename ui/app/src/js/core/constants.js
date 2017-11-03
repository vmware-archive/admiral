/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { searchConstants } from 'admiral-ui-common';

var constants = Immutable({
  LOADING: {},
  CONTEXT_PANEL: {
    PLACEMENT_ZONES: 'placementZones',
    CLOSURES: 'closures',
    CREDENTIALS: 'credentials',
    CERTIFICATES: 'certificates',
    RESOURCE_GROUPS: 'resourceGroups',
    DEPLOYMENT_POLICIES: 'deploymentPolicies',
    REQUESTS: 'requests',
    EVENTLOGS: 'eventlogs'
  },
  VIEWS: {
    HOME: {name: 'home'},
    CLOSURES: {
      name: 'closures'
    },
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
    CREDENTIALS: {
      name: 'credentials'
    },
    CERTIFICATES: {
      name: 'certificates'
    },
    REGISTRIES: {
      name: 'registries'
    },
    RESOURCES: {
      name: 'resources',
      VIEWS: {
        PROJECTS: {
          name: 'projects',
          route: 'projects'
        },
        CONTAINERS: {
          name: 'containers',
          route: 'containers',
          default: true
        },
        APPLICATIONS: {
          name: 'applications',
          route: 'applications'
        },
        NETWORKS: {
          name: 'networks',
          route: 'networks'
        },
        VOLUMES: {
          name: 'volumes',
          route: 'volumes'
        },
        CLOSURES: {
          name: 'closures',
          route: 'closures'
        },
        CLOSURES_DESC: {
          name: 'closures_desc',
          route: 'closures_desc'
        }
      }
    },
    KUBERNETES_RESOURCES: {
      name: 'kubernetesResources',
      ng: true,
      VIEWS: {
        APPLICATIONS: {
          name: 'kubernetesApplications',
          route: 'applications',
          default: true
        },
        PODS: {
          name: 'kubernetesPods',
          route: 'pods'
        },
        DEPLOYMENTS: {
          name: 'kubernetesDeployments',
          route: 'deployments'
        },
        SERVICES: {
          name: 'kubernetesServices',
          route: 'services'
        },
        REPLICATION_CONTROLLERS: {
          name: 'kubernetesReplicationControllers',
          route: 'replication-controllers'
        }
      }
    }
  },
  PLACEMENT_ZONE: {
    TYPE: {
      DOCKER: 'DOCKER',
      SCHEDULER: 'SCHEDULER'
    }
  },
  HOST: {
    TYPE: {
      DOCKER: 'DOCKER',
      VCH: 'VCH',
      KUBERNETES: 'KUBERNETES'
    },
    CUSTOM_PROPS: {
      PUBLIC_ADDRESS: '__publicAddress'
    }
  },
  CREDENTIALS_TYPE: {
    PUBLIC: 'Public',
    PUBLIC_KEY: 'PublicKey',
    PRIVATE_KEY: 'PrivateKey',
    PASSWORD: 'Password'
  },
  CREDENTIALS_SCOPE_TYPE: {
    SYSTEM: 'SYSTEM'
  },
  VISUALS: {
    ITEM_HIGHLIGHT_ACTIVE_TIMEOUT: 1500,
    ITEM_HIGHLIGHT_ACTIVE_TIMEOUT_LONG: 5000
  },
  HOSTS: {
    OPERATION: {
      ENABLE: 'ENABLE',
      DISABLE: 'DISABLE',
      DATACOLLECT: 'DATACOLLECT'
    },
    SEARCH_SUGGESTIONS: ['address']
  },
  // TODO move all entries from CONTAINERS to RESOURCES.CONTAINER
  RESOURCES: {
    TYPES: {
      PROJECT: 'PROJECT',
      NETWORK: 'NETWORK',
      VOLUME: 'VOLUME',
      CLOSURE: 'CLOSURE'
    },
    SEARCH_CATEGORY: {
      ALL: 'all',
      CONTAINERS: 'containers',
      APPLICATIONS: 'applications',
      NETWORKS: 'networks',
      VOLUMES: 'volumes',
      CLOSURES: 'closures',
      KUBERNETES: 'kubernetes',
      PROJECTS: 'projects'
    },
    KUBERNETES: {
      SEARCH_SUGGESTIONS: []
    },
    NETWORKS: {
      SEARCH_SUGGESTIONS: ['name', 'documentId', 'status'],
      OPERATION: {
        REMOVE: 'REMOVE',
        MANAGE: 'MANAGE'
      },
      STATES: {
        PROVISIONING: 'PROVISIONING',
        CONNECTED: 'CONNECTED',
        ERROR: 'ERROR',
        UNKNOWN: 'UNKNOWN',
        RETIRED: 'RETIRED'
      }
    },
    VOLUMES: {
      SEARCH_SUGGESTIONS: ['name', 'documentId', 'status'],
      OPERATION: {
        REMOVE: 'REMOVE',
        MANAGE: 'MANAGE'
      },
      STATES: {
        PROVISIONING: 'PROVISIONING',
        CONNECTED: 'CONNECTED',
        ERROR: 'ERROR',
        UNKNOWN: 'UNKNOWN',
        RETIRED: 'RETIRED'
      }
    },
    PROJECTS: {
      SEARCH_SUGGESTIONS: ['name', 'documentId'],
      OPERATION: {
        CREATE: 'CREATE',
        REMOVE: 'REMOVE',
        EDIT: 'EDIT'
      }
    },
    APPLICATIONS: {
      SEARCH_SUGGESTIONS: ['name', 'documentId']
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
    STATUS: {
        UNHEALTHY: 'unhealthy'
    },
    LOGS: {
      SINCE_DURATIONS: [
        moment.duration(15, 'minutes').asMilliseconds(),
        moment.duration(30, 'minutes').asMilliseconds(),
        moment.duration(1, 'hours').asMilliseconds(),
        moment.duration(2, 'hours').asMilliseconds(),
        moment.duration(5, 'hours').asMilliseconds()
      ],
      TAIL_LINES: [
        50,
        100,
        1000,
        10000
      ],
      OPTION: {
        SINCE: 'since',
        TAIL: 'tail'
      },
      FORMAT: {
        ANSI: 'ansi',
        RAW: 'raw'
      }
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
    SEARCH_SUGGESTIONS: ['name', 'image', 'parentId', 'documentId', 'status', 'ports', 'command'],
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
      NETWORKCREATE: 'NETWORKCREATE',
      MANAGE: 'MANAGE',
      CREATE_TEMPLATE: 'CREATE_TEMPLATE',
      OPEN_TEMPLATE: 'OPEN_TEMPLATE',
      CREATE_VOLUME: 'CREATE_VOLUME'
    },
    START_REFRESH_POLLING_DELAY: 5000,
    DEFAULT_REFRESH_INTERVAL: 5000
  },
  CLOSURES: {
    SEARCH_SUGGESTIONS: ['name', 'documentId'],
    OPERATION: {
      DETAILS: 'DETAILS'
    }
  },
  TEMPLATES: {
    TYPES: {
      TEMPLATE: 'TEMPLATE',
      IMAGE: 'IMAGE',
      CLOSURE: 'CLOSURE'
    },
    SEARCH_CATEGORY: {
      ALL: 'all',
      IMAGES: 'images',
      TEMPLATES: 'templates',
      CLOSURES: 'closures'
    },
    SEARCH_SUGGESTIONS: ['name', 'repository', 'size', 'description'],
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
  SEARCH_CATEGORY_PARAM: searchConstants.SEARCH_CATEGORY_PARAM,
  SEARCH_OCCURRENCE: searchConstants.SEARCH_OCCURRENCE,
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
  BUILT_IN_NETWORKS: {
    NONE: 'none',
    HOST: 'host',
    BRIDGE: 'bridge',
    GWBRIDGE: 'docker_gwbridge'
  },
  CUSTOM_PROPS: {
    EPZ_NAME_PREFIX: '__epz_',
    EPZ_VALUE: 'true'
  },
  RESOURCE_TYPES: {
    CONTAINER: 'DOCKER_CONTAINER',
    COMPUTE: 'COMPUTE'
  },
  NEW_ITEM_SYSTEM_VALUE: '__new',
  NO_LINK_VALUE: '__noLink',
  RESOURCE_CONNECTION_TYPE: {
    NETWORK: 'network',
    VOLUME: 'volume'
  },
  TAGS: {
    SEPARATOR: ':',
    SEPARATOR_ENTITY: '׃'
  }
});

export default constants;
