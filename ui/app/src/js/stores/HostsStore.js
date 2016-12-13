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

import * as actions from 'actions/Actions';
import services from 'core/services';
import links from 'core/links';
import constants from 'core/constants';
import utils from 'core/utils';
import PlacementZonesStore from 'stores/PlacementZonesStore';
import CredentialsStore from 'stores/CredentialsStore';
import CertificatesStore from 'stores/CertificatesStore';
import EndpointsStore from 'stores/EndpointsStore';
import EnvironmentsStore from 'stores/EnvironmentsStore';
import DeploymentPolicyStore from 'stores/DeploymentPolicyStore';
import RequestsStore from 'stores/RequestsStore';
import EventLogStore from 'stores/EventLogStore';
import NotificationsStore from 'stores/NotificationsStore';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';

const OPERATION = {
  LIST: 'LIST',
  DETAILS: 'DETAILS'
};

let hostConstraints = {
  address: function(address) {
    if (!address || validator.trim(address).length === 0) {
      return 'errors.required';
    }

    if (!validator.isURL(address, {
        require_tld: false,
        allow_underscores: true
      })) {
      return 'errors.hostIp';
    }
  },

  placementZone: function(placementZone) {
    if (!placementZone) {
      return 'errors.required';
    }
  },

  connectionType: function(connectionType) {
    if (connectionType && validator.trim(connectionType).length === 0) {
      return 'errors.required';
    }
  },

  customProperties: function(properties) {
    if (properties) {
      var keys = [];

      for (var i = 0; i < properties.length; i++) {
        if (!properties[i].name) {
          return 'errors.propertyNameRequired';
        }
        keys.push(properties[i].name);
      }

      if (utils.uniqueArray(keys).length !== keys.length) {
        return 'errors.propertyNamesNotUnique';
      }
    }
  }
};

let isContextPanelActive = function(name) {
  var activeItem = this.data.hostAddView.contextView &&
      this.data.hostAddView.contextView.activeItem;
  return activeItem && activeItem.name === name;
};

let onOpenToolbarItem = function(name, data, shouldSelectAndComplete) {
  var contextViewData = {
    expanded: true,
    activeItem: {
      name: name,
      data: data
    },
    shouldSelectAndComplete: shouldSelectAndComplete
  };

  this.setInData(['hostAddView', 'contextView'], contextViewData);
  this.emitChange();
};

let getHostSpec = function(hostModel) {
  let customProperties = {
    __adapterDockerType: hostModel.connectionType
  };

  if (hostModel.credential) {
    customProperties.__authCredentialsLink = hostModel.credential.documentSelfLink;
  }

  if (hostModel.customProperties) {

    hostModel.customProperties.forEach(function(prop) {
      customProperties[prop.name] = prop.value;
    });
  }

  var id = hostModel.address;
  id = id.replace(/(http:|https:|\/)/g, '');

  let hostState = {
    id: id,
    address: hostModel.address,
    resourcePoolLink: hostModel.resourcePoolLink,
    descriptionLink: hostModel.descriptionLink,
    customProperties: customProperties,
    powerState: hostModel.powerState,
    tagLinks: hostModel.tagLinks
  };

  let hostSpec = {
    hostState: hostState
  };

  return hostSpec;
};

let getHostAutoConfigSpec = function(hostModel) {
  var hostAutoConfigSpec = {
    __address: hostModel.address,
    __resourcePoolLink: hostModel.resourcePoolLink,
    __tagLinks: hostModel.tagLinks
  };

  if (hostModel.credential) {
    hostAutoConfigSpec.__authCredentialsLink = hostModel.credential.documentSelfLink;
  }

  if (hostModel.customProperties) {
    hostModel.customProperties.forEach(function(prop) {
      hostAutoConfigSpec[prop.name] = prop.value;
    });
  }

  return hostAutoConfigSpec;
};

let toViewModel = function(dto) {
  let customProperties = [];
  let hasCustomProperties = dto.customProperties && dto.customProperties !== null;
  if (hasCustomProperties) {
    for (var key in dto.customProperties) {
      if (dto.customProperties.hasOwnProperty(key)) {
        customProperties.push({
          name: key,
          value: dto.customProperties[key]
        });
      }
    }
  }

  var epzs = [];
  for (var i = 0; i < customProperties.length; i++) {
    if (customProperties[i].name.startsWith(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX)) {
        let epzId = customProperties[i].name.slice(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX.length);
        epzs.push({
           epzDocumentId: epzId,
           epzLink: links.PLACEMENT_ZONES + '/' + epzId
        });
    }
  }

  var memoryUsagePct;
  if (hasCustomProperties && dto.customProperties.__MemTotal
      && dto.customProperties.__MemAvailable) {
    var memoryUsage = dto.customProperties.__MemTotal - dto.customProperties.__MemAvailable;
    memoryUsagePct = (memoryUsage / dto.customProperties.__MemTotal) * 100;
    memoryUsagePct = Math.round(memoryUsagePct * 100) / 100;
  }

  var cpuUsagePct;
  if (hasCustomProperties && dto.customProperties.__CpuUsage) {
    cpuUsagePct = Math.round(dto.customProperties.__CpuUsage * 100) / 100;
  }

  var containers = '--';
  if (hasCustomProperties && dto.customProperties.__Containers) {
    containers = Math.round(dto.customProperties.__Containers);
  }
  return {
    dto: dto,
    id: dto.id,
    name: dto.name,
    address: dto.address ? dto.address : dto.id,
    descriptionLink: dto.descriptionLink,
    powerState: dto.powerState,
    resourcePoolLink: dto.resourcePoolLink,
    placementZoneDocumentId: utils.getDocumentId(dto.resourcePoolLink),
    epzs: epzs,
    containers: containers,
    connectionType: hasCustomProperties ? dto.customProperties.__adapterDockerType : null,
    memoryPercentage: memoryUsagePct,
    cpuPercentage: cpuUsagePct,
    customProperties: customProperties,
    selfLinkId: utils.getDocumentId(dto.documentSelfLink),
    tagLinks: dto.tagLinks
  };
};

let updateEditableProperties = function(hostModel) {
  this.setInData(['hostAddView', 'hostAlias'], hostModel.hostAlias);
  this.setInData(['hostAddView', 'customProperties'], hostModel.customProperties);

  var credentials = utils.getIn(this.getData(), ['hostAddView', 'credentials']);
  if (credentials && hostModel.credential) {
    var credential = credentials.find((credential) => {
      return credential.documentSelfLink === hostModel.credential.documentSelfLink;
    });
    this.setInData(['hostAddView', 'credential'], credential);
  } else {
    this.setInData(['hostAddView', 'credential'], null);
  }

  var placementZones = utils.getIn(this.getData(), ['hostAddView', 'placementZones']);
  if (placementZones && hostModel.resourcePoolLink) {
    var placementZone = placementZones.find((placementZone) => {
      return placementZone.documentSelfLink === hostModel.resourcePoolLink;
    });
    this.setInData(['hostAddView', 'placementZone'], placementZone);
  } else {
    this.setInData(['hostAddView', 'placementZone'], null);
  }

  var deploymentPolicyProp = hostModel.customProperties.find((prop) => {
    return prop.name === '__deploymentPolicyLink';
  });
  if (deploymentPolicyProp) {
    var deploymentPolicies = utils.getIn(this.getData(), ['hostAddView', 'deploymentPolicies']);
    var deploymentPolicy = deploymentPolicies.find((policy) => {
      return policy.documentSelfLink === deploymentPolicyProp.value;
    });
    this.setInData(['hostAddView', 'deploymentPolicy'], deploymentPolicy);
  } else {
    // removed
    this.setInData(['hostAddView', 'deploymentPolicy'], null);
    hostModel.customProperties.__deploymentPolicyLink = null;
  }

  var endpoints = utils.getIn(this.getData(), ['hostAddView', 'endpoints']);
  if (endpoints && hostModel.endpoint) {
    var endpoint = endpoints.find((endpoint) => {
      return endpoint.documentSelfLink === hostModel.parentLink;
    });
    this.setInData(['hostAddView', 'endpoint'], endpoint);
  } else {
    this.setInData(['hostAddView', 'endpoint'], null);
  }
};

let HostsStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
    NotificationsStore.listen((notifications) => {
      if (this.data.hostAddView) {
        return;
      }

      this.setInData(['contextView', 'notifications', constants.CONTEXT_PANEL.REQUESTS],
        notifications.runningRequestItemsCount);

      this.setInData(['contextView', 'notifications', constants.CONTEXT_PANEL.EVENTLOGS],
        notifications.latestEventLogItemsCount);

      this.emitChange();
    });

    RequestsStore.listen((requestsData) => {
      if (this.data.hostAddView) {
        return;
      }

      if (this.isContextPanelActive(constants.CONTEXT_PANEL.REQUESTS)) {
        this.setActiveItemData(requestsData);
        this.emitChange();
      }
    });

    EventLogStore.listen((eventlogsData) => {
      if (this.data.hostAddView) {
        return;
      }

      if (this.isContextPanelActive(constants.CONTEXT_PANEL.EVENTLOGS)) {
        this.setActiveItemData(eventlogsData);
        this.emitChange();
      }
    });

    PlacementZonesStore.listen((placementZonesData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'placementZones'], placementZonesData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.PLACEMENT_ZONES)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          placementZonesData);

        var itemToSelect = placementZonesData.newItem || placementZonesData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'placementZone'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    CredentialsStore.listen((credentialsData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'credentials'], credentialsData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.CREDENTIALS)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          credentialsData);

        var itemToSelect = credentialsData.newItem || credentialsData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'credential'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    DeploymentPolicyStore.listen((policiesData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'deploymentPolicies'], policiesData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          policiesData);

        var itemToSelect = policiesData.newItem || policiesData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'deploymentPolicy'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    CertificatesStore.listen((certificatesData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'certificates'], certificatesData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.CERTIFICATES)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          certificatesData);

        var itemToSelect = certificatesData.newItem || certificatesData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'certificate'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    EndpointsStore.listen((endpointsData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'endpoints'], endpointsData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.ENDPOINTS)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          endpointsData);

        var itemToSelect = endpointsData.newItem || endpointsData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'endpoint'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });


    EnvironmentsStore.listen((environmentsData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'environments'], environmentsData.items);
      this.emitChange();
    });
  },

  listenables: [actions.HostActions,
    actions.HostsContextToolbarActions,
    actions.HostContextToolbarActions
  ],

  onOpenHosts: function(queryOptions) {
    this.setInData(['listView', 'queryOptions'], queryOptions);
    this.setInData(['hostAddView'], null);
    this.setInData(['contextView'], {});
    this.setInData(['validationErrors'], null);
    this.setInData(['dataCollectionEnd'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadHosts(queryOptions, true)).then((result) => {
        // TODO: temporary client side filter
        let documents = result.documentLinks.map((documentLink) =>
            result.documents[documentLink]).filter(({customProperties}) =>
                customProperties && customProperties.__computeContainerHost);
        let nextPageLink = result.nextPageLink;
        let itemsCount = result.totalCount;

        // Transforming to the model of the view
        let hosts = documents.map((document) => toViewModel(document));
        this.getPlacementZonesForHostsCall(hosts).then((result) => {
          hosts.forEach((host) => {
            host.epzs.forEach((epz) => {
              if (result[epz.epzLink]) {
                epz.epzName = result[epz.epzLink].resourcePoolState.name;
              }
            });
          });

          this.setInData(['listView', 'items'], hosts);
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          if (itemsCount !== undefined && itemsCount !== null) {
            this.setInData(['listView', 'itemsCount'], itemsCount);
          }

          this.emitChange();
        });
      });
    }

    this.emitChange();
  },

  onOpenHostsNext: function(queryOptions, nextPageLink) {
    this.setInData(['listView', 'queryOptions'], queryOptions);

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadNextPage(nextPageLink)).then((result) => {
        let documents = result.documentLinks.map((documentLink) =>
            result.documents[documentLink]).filter(({customProperties}) =>
                customProperties && customProperties.__computeContainerHost);
        let nextPageLink = result.nextPageLink;

        // Transforming to the model of the view
        let hosts = documents.map((document) => toViewModel(document));
        this.getPlacementZonesForHostsCall(hosts).then((result) => {
          hosts.forEach((host) => {
            host.epzs.forEach((epz) => {
              if (result[epz.epzLink]) {
                epz.epzName = result[epz.epzLink].resourcePoolState.name;
              }
            });
          });

          this.setInData(['listView', 'items'],
              utils.mergeDocuments(this.data.listView.items.asMutable(), hosts));
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          this.emitChange();
        });
      });
    }

    this.emitChange();
  },

  getPlacementZonesForHostsCall: function(hosts) {
    let placementZones = utils.getIn(this.data, ['listView', 'placementZones']) || {};
    let resourcePoolLinks = [];
    hosts.forEach((host) => {
      host.epzs.forEach((epz) => resourcePoolLinks.push(epz.epzLink));
    });
    let links = [...new Set(resourcePoolLinks)].filter((link) =>
        !placementZones.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(placementZones);
    }
    return services.loadPlacementZones(links).then((newPlacementZones) => {
      this.setInData(['listView', 'placementZones'],
          $.extend({}, placementZones, newPlacementZones));
      return utils.getIn(this.data, ['listView', 'placementZones']);
    });
  },

  onOpenAddHost: function() {
    // Immediately update the view. Later the placementZones and credentials lists will be updated.
    var hostAddView = {
      contextView: {}
    };
    this.setInData(['hostAddView'], hostAddView);
    this.emitChange();

    actions.PlacementZonesActions.retrievePlacementZones();
    actions.CredentialsActions.retrieveCredentials();
    actions.CertificatesActions.retrieveCertificates();
    actions.DeploymentPolicyActions.retrieveDeploymentPolicies();
    actions.EndpointsActions.retrieveEndpoints();
    actions.EnvironmentsActions.openEnvironments();
  },

  onCloseHosts: function() {
    this.setInData(['listView', 'items'], []);
    this.setInData(['listView', 'itemsLoading'], false);
    this.setInData(['listView', 'itemsCount'], 0);
    this.setInData(['listView', 'nextPageLink'], null);
    this.setInData(['listView', 'placementZones'], null);
  },

  onAutoConfigureHost: function(hostModel) {
    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(hostModel, hostConstraints);
    if (validationErrors) {
      // propagate errors
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      let hostAutoConfigSpec = getHostAutoConfigSpec(hostModel);

      services.autoConfigureHost(hostAutoConfigSpec).then((result) => {
        // Navigate to hosts view and show requests pane
        this.navigateHostsListViewAndOpenRequests(result);
      }).catch(this.onGenericEditError);
    }

    this.emitChange();
  },

  navigateHostsListViewAndOpenRequests: function(request) {
    var openHostsUnsubscribe = actions.HostActions.openHosts.listen(() => {
      openHostsUnsubscribe();

      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(request);
    });

    actions.NavigationActions.openHosts();
  },

  onAddHost: function(hostModel, tags) {
    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(hostModel, hostConstraints);
    if (validationErrors) {
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      this.setInData(['hostAddView', 'isSavingHost'], true);

      Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
        return Promise.all(tags.map((tag, i) =>
          result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
      }).then((createdTags) => {

        hostModel.tagLinks = [...new Set(createdTags.map((tag) => tag.documentSelfLink))];

        var hostSpec = getHostSpec(hostModel);

        services.addHost(hostSpec).then((hostSpec) => {
          this.setInData(['hostAddView', 'isSavingHost'], false);
          if (hostSpec && hostSpec.certificate) {
            this.setInData(['hostAddView', 'shouldAcceptCertificate'], {
              certificateHolder: hostSpec
            });
            this.emitChange();
          } else {
            this.onHostAdded();
          }
        }).catch(this.onGenericEditError);
      });
    }

    this.emitChange();
  },

  onCreateHost: function(description, clusterSize, tags) {
    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(description, hostConstraints);
    if (validationErrors) {
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      this.setInData(['hostAddView', 'isSavingHost'], true);

      Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
        return Promise.all(tags.map((tag, i) =>
          result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
      }).then((createdTags) => {
        description.tagLinks = [...new Set(createdTags.map((tag) => tag.documentSelfLink))];

        services.createHostDescription(description).then((createdDescription) => {
          return services.createHost(createdDescription, clusterSize);
        }).then((request) => {
          // show hosts view and open requests side view
          this.navigateHostsListViewAndOpenRequests(request);
        }).catch(this.onGenericEditError);
      });
    }

    this.emitChange();
  },

  onEditHost: function(hostId) {
    var hostAddView = {
      contextView: {},
      isUpdate: true
    };
    this.setInData(['hostAddView'], hostAddView);
    this.emitChange();

    // load host data from backend
    services.loadHost(hostId).then((hostSpec) => {
      var hostModel = toViewModel(hostSpec);

      actions.PlacementZonesActions.retrievePlacementZones();
      actions.CredentialsActions.retrieveCredentials();
      actions.CertificatesActions.retrieveCertificates();
      actions.DeploymentPolicyActions.retrieveDeploymentPolicies();

      var credentialLink = hostSpec.customProperties.__authCredentialsLink;
      var deploymentPolicyLink = hostSpec.customProperties.__deploymentPolicyLink;
      this.loadHostData(hostModel, credentialLink, deploymentPolicyLink);
    }).catch(this.onGenericEditError);

    this.emitChange();
  },

  loadHostData: function(hostModel, credentialLink, deploymentPolicyLink) {
    var promises = [];

    if (hostModel.resourcePoolLink) {
        promises.push(
            services.loadPlacementZone(hostModel.resourcePoolLink).catch(() => Promise.resolve()));
    } else {
        promises.push(Promise.resolve());
    }

    if (credentialLink) {
      promises.push(services.loadCredential(credentialLink).catch(() => Promise.resolve()));
    } else {
      promises.push(Promise.resolve(credentialLink).catch(() => Promise.resolve()));
    }

    if (hostModel.customProperties && deploymentPolicyLink) {
      promises.push(
          services.loadDeploymentPolicy(deploymentPolicyLink).catch(() => Promise.resolve()));
    } else {
      promises.push(Promise.resolve());
    }

    if (hostModel.tagLinks && hostModel.tagLinks.length) {
      promises.push(
          services.loadTags(hostModel.tagLinks).catch(() => Promise.resolve()));
    } else {
      promises.push(Promise.resolve());
    }

    Promise.all(promises).then(([config, credential, deploymentPolicy, tags]) => {

      if (credentialLink && credential) {
        credential.name = (credential.customProperties
            && credential.customProperties.__authCredentialsName)
            ? credential.customProperties.__authCredentialsName
            : utils.getDocumentId(credentialLink);
        hostModel.credential = credential;
      }
      if (hostModel.resourcePoolLink && config) {
          hostModel.placementZone = config.resourcePoolState;
      }
      hostModel.deploymentPolicy = deploymentPolicy;

      // preselection of resource pool and credentials
      var hostAddView = {
        dto: hostModel.dto,
        id: hostModel.id,
        hostAlias: utils.getHostName(hostModel),
        address: hostModel.address ? hostModel.address : hostModel.id,
        placementZone: hostModel.placementZone,
        credential: credential,
        deploymentPolicy: hostModel.deploymentPolicy,
        connectionType: hostModel.connectionType,
        customProperties: utils.getDisplayableCustomProperties(hostModel.customProperties),
        descriptionLink: hostModel.descriptionLink,
        powerState: hostModel.powerState,
        selfLinkId: hostModel.selfLinkId,
        tags: tags ? Object.values(tags) : []
      };

      this.setInData(['hostAddView'], $.extend({}, this.data.hostAddView, hostAddView));
      this.emitChange();
    }).catch(this.onGenericEditError);
  },

  onUpdateHost: function(hostModel, tags) {
    updateEditableProperties.call(this, hostModel);

    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(hostModel, hostConstraints);
    if (validationErrors) {
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      this.setInData(['hostAddView', 'isSavingHost'], true);

      var hostId = hostModel.selfLinkId ? hostModel.selfLinkId : hostModel.address;
      hostId = hostId.replace(/\//g, '');

      // the only thing currently editable are the custom properties
      let customProperties = {};
      if (hostModel.customProperties) {
        customProperties = utils.arrayToObject(hostModel.customProperties);
      }

      let hostName = utils.getCustomPropertyValue(hostModel.customProperties, '__Name');
      if (hostModel.hostAlias !== hostName) {
        customProperties.__hostAlias = hostModel.hostAlias;
      } else {
        delete customProperties.__hostAlias;
      }

      Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
        return Promise.all(tags.map((tag, i) =>
          result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
      }).then((updatedTags) => {
        let hostDataCustomProperties = $.extend({},
          utils.getSystemCustomProperties(hostModel.dto.customProperties), customProperties);

        let deploymentPolicy =
          utils.getCustomPropertyValue(hostModel.customProperties, '__deploymentPolicyLink');
        if (!deploymentPolicy) {
          delete hostDataCustomProperties.__deploymentPolicyLink;
        }

        let credentials =
          utils.getCustomPropertyValue(hostModel.customProperties, '__authCredentialsLink');
        if (!credentials) {
          delete hostDataCustomProperties.__authCredentialsLink;
        }

        var hostData = $.extend({}, hostModel.dto, {
          customProperties: hostDataCustomProperties,
          descriptionLink: this.data.hostAddView.descriptionLink,
          resourcePoolLink: hostModel.resourcePoolLink,
          credential: hostModel.credential,
          powerState: this.data.hostAddView.powerState,
          tagLinks: [...new Set(updatedTags.map((tag) => tag.documentSelfLink))]
        });
        var hostSpec = {
          hostState: hostData,
          isUpdateOperation: true
        };

        services.updateContainerHost(hostSpec).then(() => {
          this.onHostAdded();
        }).catch(this.onGenericEditError);
      });

    }

    this.emitChange();
  },

  onVerifyHost: function(hostModel) {
    updateEditableProperties.call(this, hostModel);

    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(hostModel, hostConstraints);
    if (validationErrors) {
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      this.setInData(['hostAddView', 'isVerifyingHost'], true);

      var hostSpec = getHostSpec(hostModel);

      services.validateHost(hostSpec).then((hostSpec) => {
        this.setInData(['hostAddView', 'isVerifyingHost'], false);
        if (hostSpec && hostSpec.certificate) {
          this.setInData(['hostAddView', 'shouldAcceptCertificate'], {
            certificateHolder: hostSpec,
            verify: true
          });
          this.emitChange();
        } else {
          this.setInData(['hostAddView', 'validationErrors', '_valid'], true);
          this.emitChange();
        }
      }).catch(this.onGenericEditError);
    }

    this.emitChange();
  },

  onDisableHost: function(hostId) {
    services.disableHost(hostId)
      .then(() => {
        // refresh hosts view
        actions.HostActions.openHosts();
      });
  },

  onEnableHost: function(hostId) {
    services.enableHost(hostId)
      .then(() => {
        // refresh hosts view
        actions.HostActions.openHosts();
      });
  },

  onRemoveHost: function(hostId) {
    services.removeHost(hostId).then((removalRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(removalRequest);
    });
  },

  onAcceptCertificateAndAddHost: function(certificateHolder, hostModel, tags) {
    this.setInData(['hostAddView', 'isSavingHost'], true);
    this.emitChange();

    services.createCertificate(certificateHolder)
      .then(() => {
        this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);
        this.setInData(['hostAddView', 'isSavingHost'], true);
        this.emitChange();

        return Promise.all(tags.map((tag) =>
            services.loadTag(tag.key, tag.value))).then((result) => {
          return Promise.all(tags.map((tag, i) =>
            result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
        }).then((createdTags) => {

          hostModel.tagLinks = [...new Set(createdTags.map((tag) => tag.documentSelfLink))];

          var hostSpec = getHostSpec(hostModel);
          hostSpec.sslTrust = certificateHolder;

          return services.addHost(hostSpec);
        });
      })
      .then(this.onHostAdded)
      .catch(this.onGenericEditError);
  },

  onOperationCompleted: function() {
    // TODO perform refresh only on the host item box, not whole screen
    this.onOpenHosts();
  },

  onAcceptCertificateAndVerifyHost: function(certificateHolder, hostModel) {
    this.emitChange();

    services.createCertificate(certificateHolder)
      .then(() => {
        this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);
        this.setInData(['hostAddView', 'isVerifyingHost'], true);
        this.emitChange();

        var hostSpec = getHostSpec(hostModel);
        hostSpec.sslTrust = certificateHolder;

        return services.validateHost(hostSpec);
      })
      .then(() => {
        this.setInData(['hostAddView', 'validationErrors', '_valid'], true);
        this.setInData(['hostAddView', 'isVerifyingHost'], false);
        this.emitChange();
      })
      .catch(this.onGenericEditError);
  },

  onHostAdded: function() {
    actions.NavigationActions.openHosts();
    this.setInData(['hostAddView', 'isSavingHost'], false);
    this.emitChange();
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    this.setInData(['hostAddView', 'isSavingHost'], false);
    this.setInData(['hostAddView', 'isVerifyingHost'], false);
    this.emitChange();
  },

  onOpenToolbarPlacementZones: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.PLACEMENT_ZONES,
      PlacementZonesStore.getData(), false);
  },

  onOpenToolbarCredentials: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(),
      false);
  },

  onOpenToolbarCertificates: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CERTIFICATES, CertificatesStore.getData(),
      false);
  },

  onOpenToolbarEndpoints: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
      EndpointsStore.getData(), false);
  },

  onOpenToolbarDeploymentPolicies: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), false);
  },

  onOpenToolbarRequests: function() {
    actions.RequestsActions.openRequests();
    this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
  },

  onOpenToolbarEventLogs: function(highlightedItemLink) {
    actions.EventLogActions.openEventLog(highlightedItemLink);
    this.openToolbarItem(constants.CONTEXT_PANEL.EVENTLOGS, EventLogStore.getData());
  },

  onCloseToolbar: function() {
    if (!this.data.hostAddView) {

      this.closeToolbar();
    } else {

      var contextViewData = {
        expanded: false,
        activeItem: null
      };

      this.setInData(['hostAddView', 'contextView'], contextViewData);
      this.emitChange();
    }
  },

  onCreatePlacementZone: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.PLACEMENT_ZONES,
     PlacementZonesStore.getData(), true);
    actions.PlacementZonesActions.editPlacementZone({});
  },

  onManagePlacementZones: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.PLACEMENT_ZONES,
     PlacementZonesStore.getData(), true);
  },

  onCreateCredential: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(),
      true);
    actions.CredentialsActions.editCredential();
  },

  onManageCredentials: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(),
      true);
  },

  onManageCertificates: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CERTIFICATES, CertificatesStore.getData(),
      true);
  },

  onCreateDeploymentPolicy: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), true);
    actions.DeploymentPolicyActions.editDeploymentPolicy();
  },

  onManageDeploymentPolicies: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), true);
  },

  onCreateEndpoint: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
      EndpointsStore.getData(), true);
    actions.EndpointsActions.editEndpoint({});
  },

  onManageEndpoints: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
      EndpointsStore.getData(), true);
  },

  onTriggerDataCollection: function() {
    let validationErrors = {
      _valid: i18n.t('app.host.list.dataCollectStart')
    };
    this.setInData(['validationErrors'], validationErrors);
    this.setInData(['dataCollectionEnd'], null);
    this.emitChange();

    services.triggerDataCollection()
      .then(() => {
        // refresh hosts view
        actions.HostActions.openHosts();

        let validationErrors = {
          _valid: i18n.t('app.host.list.dataCollectEnd')
        };
        this.setInData(['validationErrors'], validationErrors);
        this.setInData(['dataCollectionEnd'], true);
        this.emitChange();
      })
      .catch((e) => {
        let validationErrors = utils.getValidationErrors(e);
        this.setInData(['validationErrors'], validationErrors);
        this.setInData(['dataCollectionEnd'], true);
        this.emitChange();
      });
  }

});

export default HostsStore;
