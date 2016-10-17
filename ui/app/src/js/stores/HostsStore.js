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
import ResourcePoolsStore from 'stores/ResourcePoolsStore';
import CredentialsStore from 'stores/CredentialsStore';
import CertificatesStore from 'stores/CertificatesStore';
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

  resourcePool: function(resourcePool) {
    if (!resourcePool) {
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
  id = id.replace(/\//g, '');

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

  // TODO: handle multiple EPZs
  var epzId = null;
  for (var i = 0; i < customProperties.length; i++) {
    if (customProperties[i].name.startsWith(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX)) {
        epzId = customProperties[i].name.slice(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX.length);
        break;
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
    address: dto.address ? dto.address : dto.id,
    descriptionLink: dto.descriptionLink,
    powerState: dto.powerState,
    resourcePoolLink: dto.resourcePoolLink,
    resourcePoolDocumentId: utils.getDocumentId(dto.resourcePoolLink),
    epzLink: links.RESOURCE_POOLS + '/' + epzId,
    epzDocumentId: epzId,
    containers: containers,
    connectionType: hasCustomProperties ? dto.customProperties.__adapterDockerType : null,
    memoryPercentage: memoryUsagePct,
    cpuPercentage: cpuUsagePct,
    customProperties: customProperties,
    selfLinkId: utils.getDocumentId(dto.documentSelfLink),
    tagLinks: dto.tagLinks
  };
};

function mergeItems(items1, items2) {
  return items1.concat(items2).filter((item, index, self) =>
      self.findIndex((c) => c.id === item.id) === index);
}

let updateEditableProperties = function(hostModel) {
  this.setInData(['hostAddView', 'hostAlias'], hostModel.hostAlias);
  this.setInData(['hostAddView', 'customProperties'], hostModel.customProperties);

  var credentials = utils.getIn(this.getData(), ['hostAddView', 'credentials']);

  if (hostModel.credential) {
    var credential = credentials.find((credential) => {
      return credential.documentSelfLink === hostModel.credential.documentSelfLink;
    });
  }

  this.setInData(['hostAddView', 'credential'], credential);

  var resourcePools =
      utils.getIn(this.getData(), ['hostAddView', 'resourcePools']);
  var resourcePool = resourcePools.find((resourcePool) => {
    return resourcePool.documentSelfLink === hostModel.resourcePoolLink;
  });
  this.setInData(['hostAddView', 'resourcePool'], resourcePool);

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

    ResourcePoolsStore.listen((resourcePoolsData) => {
      if (!this.data.hostAddView) {
        return;
      }

      this.setInData(['hostAddView', 'resourcePools'], resourcePoolsData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS)) {
        this.setInData(['hostAddView', 'contextView', 'activeItem', 'data'],
          resourcePoolsData);

        var itemToSelect = resourcePoolsData.newItem || resourcePoolsData.updatedItem;
        if (itemToSelect && this.data.hostAddView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['hostAddView', 'resourcePool'], itemToSelect);
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
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

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

    let isCreatingHost = this.selectFromData(['listView', 'isCreatingHostRequest']).get();
    if (isCreatingHost) {
      this.onOpenToolbarRequests();

      this.setInData(['listView', 'isCreatingHostRequest'], false);
      this.emitChange();
    }

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadHosts(queryOptions, true))
        .then((result) => {
          // TODO: temporary client side filter
          let documents = result.documentLinks.map((documentLink) =>
              result.documents[documentLink]).filter(({customProperties}) =>
                  customProperties && customProperties.__computeContainerHost);
          let nextPageLink = result.nextPageLink;
          let itemsCount = result.totalCount;

          // Transforming to the model of the view
          let hosts = documents.map((document) => toViewModel(document));
          this.getResourcePoolsForHostsCall(hosts).then((result) => {
            hosts.forEach((host) => {
              if (result[host.epzLink]) {
                host.epzName =
                    result[host.epzLink].resourcePoolState.name;
              }
            });

            this.setInData(['listView', 'items'], hosts);
            this.setInData(['listView', 'itemsLoading'], false);
            this.setInData(['listView', 'nextPageLink'], nextPageLink);
            if (itemsCount !== undefined) {
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

      operation.forPromise(services.loadNextPage(nextPageLink))
        .then((result) => {

          // TODO: temporary client side filter
          let documents = result.documentLinks.map((documentLink) =>
              result.documents[documentLink]).filter(({customProperties}) =>
                  customProperties && customProperties.__computeContainerHost);
          let nextPageLink = result.nextPageLink;

          // Transforming to the model of the view
          let hosts = mergeItems(this.data.listView.items.asMutable(),
              documents.map((document) => toViewModel(document)));
          this.getResourcePoolsForHostsCall(hosts).then((result) => {
            hosts.forEach((host) => {
              if (result[host.epzLink] && !host.epzName) {
                host.epzName = result[host.epzLink].name;
              }
            });

            this.setInData(['listView', 'items'], hosts);
            this.setInData(['listView', 'itemsLoading'], false);
            this.setInData(['listView', 'nextPageLink'], nextPageLink);

            this.emitChange();
          });
        });
    }

    this.emitChange();
  },

  getResourcePoolsForHostsCall: function(hosts) {
    let resourcePools = utils.getIn(this.data, ['listView', 'resourcePools']) || {};
    let resourcePoolLinks = hosts.filter((host) =>
        host.epzLink).map((host) => host.epzLink);
    let links = [...new Set(resourcePoolLinks)].filter((link) =>
        !resourcePools.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(resourcePools);
    }
    return services.loadResourcePools(links).then((newResourcePools) => {
      this.setInData(['listView', 'resourcePools'], $.extend({}, resourcePools, newResourcePools));
      return utils.getIn(this.data, ['listView', 'resourcePools']);
    });
  },

  onOpenAddHost: function() {
    // Immediately update the view. Later the resourcePools and credentials lists will be updated.
    var hostAddView = {
      contextView: {}
    };
    this.setInData(['hostAddView'], hostAddView);
    this.emitChange();

    actions.ResourcePoolsActions.retrieveResourcePools();
    actions.CredentialsActions.retrieveCredentials();
    actions.CertificatesActions.retrieveCertificates();
    actions.DeploymentPolicyActions.retrieveDeploymentPolicies();
  },

  onCloseHosts: function() {
    this.setInData(['listView', 'items'], []);
    this.setInData(['listView', 'itemsLoading'], false);
    this.setInData(['listView', 'itemsCount'], 0);
    this.setInData(['listView', 'nextPageLink'], null);
    this.setInData(['listView', 'resourcePools'], null);
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

  onCreateHost: function(hostModel) {
    this.setInData(['hostAddView', 'validationErrors'], null);
    this.setInData(['hostAddView', 'shouldAcceptCertificate'], null);

    var validationErrors = utils.validate(hostModel, hostConstraints);
    if (validationErrors) {
      this.setInData(['hostAddView', 'validationErrors'], validationErrors);
    } else {
      this.setInData(['hostAddView', 'isSavingHost'], true);

      services.createHostDescription(hostModel).then((hostDescription) => {
        services.createHost(hostDescription, hostModel.clusterSize).then((request) => {
          console.log('started request for Create Host: ' + request);

          this.setInData(['listView', 'isCreatingHostRequest'], true);
          this.emitChange();

          this.onHostAdded();
        });

      }).catch(this.onGenericEditError);
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

      actions.ResourcePoolsActions.retrieveResourcePools();
      actions.CredentialsActions.retrieveCredentials();
      actions.CertificatesActions.retrieveCertificates();
      actions.DeploymentPolicyActions.retrieveDeploymentPolicies();

      var _this = this;
      var credentialLink = hostSpec.customProperties.__authCredentialsLink;
      var deploymentPolicyLink = hostSpec.customProperties.__deploymentPolicyLink;
      if (!credentialLink) {
        // for amazon hosts the credential is stored in the description,
        // not in the compute instance
        services.loadHostDescriptionByLink(hostSpec.descriptionLink).then((hostDescription) => {
          credentialLink = hostDescription.customProperties
                            && hostDescription.customProperties.__authCredentialsLink;
          deploymentPolicyLink = hostDescription.customProperties
                            && hostDescription.customProperties.__deploymentPolicyLink;

          _this.loadHostData(hostModel, credentialLink, deploymentPolicyLink);
        }).catch(this.onGenericEditError);
      } else {
        this.loadHostData(hostModel, credentialLink, deploymentPolicyLink);
      }
    }).catch(this.onGenericEditError);

    this.emitChange();
  },

  loadHostData: function(hostModel, credentialLink, deploymentPolicyLink) {
    var _this = this;

    var promises = [
      services.loadResourcePool(hostModel.resourcePoolLink).catch(() => Promise.resolve())
    ];

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

    Promise.all(promises).then(function([config, credential, deploymentPolicy, tags]) {

      if (credentialLink && credential) {
        credential.name = (credential.customProperties
            && credential.customProperties.__authCredentialsName)
            ? credential.customProperties.__authCredentialsName
            : utils.getDocumentId(credentialLink);
        hostModel.credential = credential;
      }
      hostModel.resourcePool = config.resourcePoolState;
      hostModel.deploymentPolicy = deploymentPolicy;

      // preselection of resource pool and credentials
      var hostAddView = {
        dto: hostModel.dto,
        id: hostModel.id,
        hostAlias: utils.getHostName(hostModel),
        address: hostModel.address ? hostModel.address : hostModel.id,
        resourcePool: hostModel.resourcePool,
        credential: credential,
        deploymentPolicy: hostModel.deploymentPolicy,
        connectionType: hostModel.connectionType,
        customProperties: utils.getDisplayableCustomProperties(hostModel.customProperties),
        descriptionLink: hostModel.descriptionLink,
        powerState: hostModel.powerState,
        selfLinkId: hostModel.selfLinkId,
        tags: tags ? Object.values(tags) : []
      };

      _this.setInData(['hostAddView'], $.extend({}, _this.data.hostAddView, hostAddView));

      _this.emitChange();
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

        let deploymentPolicy = utils.getCustomPropertyValue(hostModel.customProperties,
                                                              '__deploymentPolicyLink');
        if (!deploymentPolicy) {
          delete hostDataCustomProperties.__deploymentPolicyLink;
        }

        var hostData = $.extend({}, hostModel.dto, {
          customProperties: hostDataCustomProperties,
          descriptionLink: this.data.hostAddView.descriptionLink,
          resourcePoolLink: hostModel.resourcePoolLink,
          credential: hostModel.credential,
          powerState: this.data.hostAddView.powerState,
          tagLinks: [...new Set(updatedTags.map((tag) => tag.documentSelfLink))]
        });

        services.updateHost(hostId, hostData).then(() => {
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

    services.removeHost(hostId)
      .then((removalRequest) => {

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

  onHostRemovalCompleted: function(hostId) {
    // TODO perform refresh only on the host item box, not whole screen
    console.log('Host id: ' + hostId + ' just got removed.');

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

  onOpenToolbarResourcePools: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
      ResourcePoolsStore.getData(), false);
  },

  onOpenToolbarCredentials: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CREDENTIALS, CredentialsStore.getData(),
      false);
  },

  onOpenToolbarCertificates: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.CERTIFICATES, CertificatesStore.getData(),
      false);
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

  onCreateResourcePool: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
     ResourcePoolsStore.getData(), true);
    actions.ResourcePoolsActions.editResourcePool();
  },

  onManageResourcePools: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
     ResourcePoolsStore.getData(), true);
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
