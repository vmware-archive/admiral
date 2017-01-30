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
import constants from 'core/constants';
import RequestsStore from 'stores/RequestsStore';
import NotificationsStore from 'stores/NotificationsStore';
import EventLogStore from 'stores/EventLogStore';
import PlacementsStore from 'stores/PlacementsStore';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import utils from 'core/utils';
import imageUtils from 'core/imageUtils';
import links from 'core/links';

const CHECK_INTERVAL_MS = 1000;

function getContainersImageIcons(containers) {
  let icons = new Set();
  for (var c in containers) {
    if (containers.hasOwnProperty(c)) {
      icons.add(imageUtils.getImageIconLink(containers[c].image));
    }
  }
  return [...icons];
}

function getHostByLink(hosts, hostLink) {
  if (!hosts) {
    return null;
  }

  return hosts.find((host) => {
    return host.documentSelfLink === hostLink;
  });
}

function enhanceContainer(container, clusterId) {
  container.icon = imageUtils.getImageIconLink(container.image);
  container.documentId = utils.getDocumentId(container.documentSelfLink);

  container.type = constants.CONTAINERS.TYPES.SINGLE;
  if (clusterId) {
    container.clusterId = clusterId;
  }

  if (container.attributes) {
    container.attributes.Config = JSON.parse(container.attributes.Config);
    container.attributes.NetworkSettings = JSON.parse(container.attributes.NetworkSettings);
    container.attributes.HostConfig = JSON.parse(container.attributes.HostConfig);
  }

  processContainerBuiltinNetworks(container);

  return container;
}

// extract any host built-in networks from container.networks to container.builtinNetworks
function processContainerBuiltinNetworks(container) {
  if (container.networks) {
    let builtinNetworks = {};

    Object.keys(container.networks).forEach((network) => {
      if (utils.isBuiltinNetwork(network)) {
        builtinNetworks[network] = container.networks[network];
        delete container.networks[network];
      }
    });

    if (Object.keys(builtinNetworks).length > 0) {
      container.builtinNetworks = builtinNetworks;
    }
  }
}

function decorateContainerHostName(container, hosts) {
  let host = getHostByLink(hosts, container.parentLink);
  if (host) {
    container.hostName = utils.getHostName(host);
    container.hostAddress = host.address;
    container.hostDocumentId = utils.getDocumentId(host.documentSelfLink);
  }
  return container;
}

function enhanceCompositeComponent(compositeComponent) {
  compositeComponent.icons = [];
  compositeComponent.documentId = utils.getDocumentId(compositeComponent.documentSelfLink);
  compositeComponent.type = constants.CONTAINERS.TYPES.COMPOSITE;

  return compositeComponent;
}

function enhanceNetwork(network) {
  network.icon = imageUtils.getImageIconLink(network.name);
  network.documentId = utils.getDocumentId(network.documentSelfLink);

  network.connectedContainers = [];
  if (network.containerStateLinks) {
    network.connectedContainers = network.containerStateLinks.map((documentSelfLink) => {
        return {
          documentId: utils.getDocumentId(documentSelfLink)
        };
    });
  }

  network.type = constants.RESOURCES.TYPES.NETWORK;
  return network;
}

function enhanceVolume(volume) {
  volume.icon = imageUtils.getImageIconLink(volume.name);
  volume.documentId = utils.getDocumentId(volume.documentSelfLink);
  volume.type = constants.RESOURCES.TYPES.VOLUME;

  volume.connectedContainers = [];
  if (volume.containerStateLinks) {
    volume.connectedContainers = volume.containerStateLinks.map((documentSelfLink) => {
      return {
        documentId: utils.getDocumentId(documentSelfLink)
      };
    });
  }

  return volume;
}

function enhanceClosure(closure) {
  closure.icon = imageUtils.getImageIconLink(closure.name);
  closure.documentId = utils.getDocumentId(closure.documentSelfLink);

  closure.type = constants.RESOURCES.TYPES.CLOSURE;
  return closure;
}

function enhanceClosureDesc(closureDescription) {
  closureDescription.icon = imageUtils.getImageIconLink(closureDescription.name);
  closureDescription.documentId = utils.getDocumentId(closureDescription.documentSelfLink);

  closureDescription.type = constants.RESOURCES.TYPES.CLOSURE_DESC;
  return closureDescription;
}

function enhanceClosureWithDescription(closure, closureDescription) {
  closure = enhanceClosure(closure);
  closure.name = closureDescription.name;
  closure.runtime = closureDescription.runtime;
  closure.description = closureDescription.description;
  closure.source = closureDescription.source;
  closure.sourceURL = closureDescription.sourceURL;
  closure.descriptionId = closureDescription.documentSelfLink;
  closure.resources = closureDescription.resources;
  return closure;
}

function getSelectedItemDetailsCursor() {
  var selectedItemDetailsCursor = this.selectFromData(['selectedItemDetails']);
  var selectedItemDetails = selectedItemDetailsCursor.get();
  if (!selectedItemDetails) {
    return null;
  }
  while (selectedItemDetails.selectedItemDetails) {
    selectedItemDetailsCursor = selectedItemDetailsCursor.select(['selectedItemDetails']);
    selectedItemDetails = selectedItemDetails.selectedItemDetails;
  }
  return selectedItemDetailsCursor;
}

function getSelectedContainerDetailsCursor() {
  var selectedItemDetailsCursor = getSelectedItemDetailsCursor.call(this);
  if (!selectedItemDetailsCursor ||
      selectedItemDetailsCursor.get().type !== constants.CONTAINERS.TYPES.SINGLE) {
    return null;
  }
  return selectedItemDetailsCursor;
}

function updateSelectedContainerDetails(path, value) {
  var selectedItemDetailsCursor = getSelectedContainerDetailsCursor.call(this);
  selectedItemDetailsCursor.setIn(path, value);
}

function findContextId(containers) {
  if (containers) {
    for (let key in containers) {
      if (!containers.hasOwnProperty(key)) {
        continue;
      }

      var container = containers[key];
      if (container.customProperties && container.customProperties.__composition_context_id) {
        return container.customProperties.__composition_context_id;
      }
    }
  }

  return null;
}

function makeClusterId(descriptionLink, compositeContextId, containers) {
  let clusterDescriptionLink = (descriptionLink.indexOf(links.CONTAINER_DESCRIPTIONS) > -1)
                      ? descriptionLink
                      : links.CONTAINER_DESCRIPTIONS + '/' + descriptionLink;

  if (!compositeContextId) {
    compositeContextId = findContextId(containers);
  }

  var clusterId = clusterDescriptionLink;
  if (compositeContextId != null) {
    clusterId += '__' + compositeContextId;
  }

  return clusterId;
}

function getDescriptionLinkFromClusterId(clusterId) {
  let containerDescriptionLink = clusterId;

  let idxSeparator = clusterId.indexOf('__');
  if (idxSeparator > -1) {
    containerDescriptionLink = clusterId.substring(0, idxSeparator);
  }

  return containerDescriptionLink;
}

function getContextIdFromClusterId(clusterId) {
  let contextId = null;

  let idxSeparator = clusterId.indexOf('__');
  if (idxSeparator > -1) {
    contextId = clusterId.substring(idxSeparator + 2, clusterId.length);
  }

  return contextId;
}

function makeClusterObject(clusterId, containers) {
  var clusterObject = {
    documentSelfLink: clusterId,
    descriptionLink: clusterId,
    name: containers ? containers[0].image : 'N/A',
    type: constants.CONTAINERS.TYPES.CLUSTER,
    containers: containers,
    networks: containers ? containers[0].networks : {},
    compositeComponentId: getContextIdFromClusterId(clusterId)
  };

  clusterObject.icon = containers ? imageUtils.getImageIconLink(containers[0].image) : null;
  clusterObject.documentId = utils.getDocumentId(clusterId);

  return clusterObject;

}

function getClusterSize(containers) {
  var clusterSize = 0;
  if (containers) {
    for (let key in containers) {
      if (!containers.hasOwnProperty(key)) {
        continue;
      }

      clusterSize++;
    }
  }

  return clusterSize;
}

function getSelectedItemContainers(selectedItem) {
  let shownItems = selectedItem.listView && selectedItem.listView.items || selectedItem.items;
  if (!shownItems) {
    return [];
  }

  if (selectedItem.type === constants.CONTAINERS.TYPES.COMPOSITE) {
      let allContainers = [];
      shownItems.forEach((item) => {
        if (item.type === constants.CONTAINERS.TYPES.CLUSTER) {
          allContainers.push.apply(allContainers, item.containers);
        } else {
          allContainers.push(item);
        }
      });

    return allContainers;
  } else if (selectedItem.type === constants.CONTAINERS.TYPES.CLUSTER) {
    return shownItems;
  } else {
    return [selectedItem];
  }
}

function isEverythingRemoved(selectedItem, operationType, removedIds) {
  if (!selectedItem) { // nothing is selected
    return false;
  }

  if (operationType !== constants.CONTAINERS.OPERATION.REMOVE) { // op is not remove
    return false;
  }

  // an application gets removed
  if ((removedIds.length === 1)
        && selectedItem.type === constants.CONTAINERS.TYPES.COMPOSITE
        && selectedItem.documentId === removedIds[0]) {
    // the application itself has been deleted
    return true;
  }

  let previousItems = selectedItem.listView && selectedItem.listView.items;
  if (selectedItem.type === constants.CONTAINERS.TYPES.COMPOSITE
        && previousItems.length === 1
        && previousItems[0].type === constants.CONTAINERS.TYPES.CLUSTER) {
    // we had only one item and it was a cluster
    return true;
  }

  let selectedItemContainers = getSelectedItemContainers(selectedItem);
  if (selectedItemContainers.length !== removedIds.length) {
    // not everything is deleted
    return false;

  } else {
    let remainingItems = selectedItemContainers.filter((item) => {
      return (removedIds.indexOf(item.documentId) === -1);
    });

    return remainingItems.length < 1;
  }
}

function redirectToCatalogItem(catalogItemId) {
  let currentURL = window.top.location.href;
  let redirectURL =
      currentURL.substring(0, currentURL
                           .indexOf('com.vmware.vcac.components.container'));
  redirectURL += 'csp.catalog.item.details%5BresourceId:=' + catalogItemId;
  window.top.location.href = redirectURL;
}

let getNetworkLinks = function(containerOrCluster, networks) {
  var networkLinks = {};
  for (var i = 0; i < containerOrCluster.length; i++) {
    var container = containerOrCluster[i];
    var containerNetworks = container.networks || {};

    for (var networkName in containerNetworks) {
      if (!containerNetworks.hasOwnProperty(networkName)) {
        continue;
      }

      var cNetworks = networks.filter(n => n.name === networkName);
      if (cNetworks.length !== 1) {
        continue;
      }

      if (!networkLinks[container.documentSelfLink]) {
        networkLinks[container.documentSelfLink] = [];
      }

      networkLinks[container.documentSelfLink].push(cNetworks[0].documentSelfLink);
    }
  }
  return networkLinks;
};

let ContainersStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],
  init: function() {
    NotificationsStore.listen((notifications) => {
      this.setInData(['contextView', 'notifications', constants.CONTEXT_PANEL.REQUESTS],
        notifications.runningRequestItemsCount);

      this.setInData(['contextView', 'notifications', constants.CONTEXT_PANEL.EVENTLOGS],
        notifications.latestEventLogItemsCount);

      this.emitChange();
    });

    RequestsStore.listen((requestsData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.REQUESTS)) {
        this.setActiveItemData(requestsData);
        this.emitChange();
      }
    });

    EventLogStore.listen((eventlogsData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.EVENTLOGS)) {
        this.setActiveItemData(eventlogsData);
        this.emitChange();
      }
    });

    PlacementsStore.listen((placementsData) => {
      if (this.data.creatingResource) {
        this.setInData(['creatingResource', 'placements'], placementsData.items);
        this.emitChange();
      }
    });
  },

  listenables: [
    actions.ContainerActions,
    actions.NetworkActions,
    actions.VolumeActions,
    actions.RegistryActions,
    actions.ContainersContextToolbarActions
  ],

  decorateContainers: function(result, category, mergeWithExisting) {
    let itemsCount = result.totalCount;
    let nextPageLink = result.nextPageLink;

    let items = result.documentLinks.map((documentLink) => {
          return result.documents[documentLink];
        });

    if (category === constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS ||
        category === constants.RESOURCES.SEARCH_CATEGORY.NETWORKS ||
        category === constants.RESOURCES.SEARCH_CATEGORY.VOLUMES ||
        category === constants.RESOURCES.SEARCH_CATEGORY.CLOSURES) {

      let enhanceFunction;
      switch (category) {
        case constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS:
          enhanceFunction = enhanceContainer;
          break;
        case constants.RESOURCES.SEARCH_CATEGORY.NETWORKS:
          enhanceFunction = enhanceNetwork;
          break;
        case constants.RESOURCES.SEARCH_CATEGORY.VOLUMES:
          enhanceFunction = enhanceVolume;
          break;
        case constants.RESOURCES.SEARCH_CATEGORY.CLOSURES:
          enhanceFunction = enhanceClosureDesc;
          break;
      }

      items.forEach((resource) => {
        enhanceFunction(resource);
      });

      let previousItems = this.selectFromData(['listView', 'items']).get();

      let mergedItems = (previousItems && mergeWithExisting)
        ? utils.mergeDocuments(previousItems.asMutable(), items) : items;

      this.setInData(['listView', 'items'], mergedItems);
      this.setInData(['listView', 'itemsLoading'], false);
      if (itemsCount !== undefined && itemsCount !== null) {
        this.setInData(['listView', 'itemsCount'], itemsCount);
      }
      this.setInData(['listView', 'nextPageLink'], nextPageLink);

      this.emitChange();

      if (category === constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS) {
        // retrieve host names
        this.getHostsForContainersCall(items).then((hosts) => {
          items.forEach((container) => {
            decorateContainerHostName(container, utils.resultToArray(hosts));
          });
          this.setInData(['listView', 'items'], mergedItems);
          this.emitChange();
        }).catch(this.onListError);
      }
    } else {
      let compositeComponentsContainersCalls = [];

      items.forEach((item) => {
        enhanceCompositeComponent(item);
        compositeComponentsContainersCalls.push(
          services.loadContainersForCompositeComponent(item.documentSelfLink));
      });
      // Load containers of the current composite components
      Promise.all(compositeComponentsContainersCalls).then((containersResults) => {

        for (let i = 0; i < containersResults.length; i++) {
          let containers = containersResults[i].documentLinks.map((documentLink) => {
            return containersResults[i].documents[documentLink];
          });

          if (containers.length > 0) {
            let hostLinks = [...new Set(containers.map((container) => container.parentLink))];
            // Assign the containers to the resp. composite component
            let compositeComponentLink = containers[0].compositeComponentLink;

            let compositeComponent = items.find((item) => {
              return item.documentSelfLink === compositeComponentLink;
            });

            if (compositeComponent) {
              compositeComponent.containers = containers;
              compositeComponent.icons = getContainersImageIcons(containers);
              if (hostLinks.length > 0) {
                compositeComponent.hostLinks = hostLinks;
              }
            }
          }
        }

        let previousItems = this.selectFromData(['listView', 'items']).get();
        let mergedItems = (previousItems && mergeWithExisting)
                            ? utils.mergeDocuments(previousItems.asMutable(), items) : items;

        this.setInData(['listView', 'items'], mergedItems);
        this.setInData(['listView', 'itemsLoading'], false);
        if (itemsCount !== undefined) {
          this.setInData(['listView', 'itemsCount'], itemsCount);
        }
        this.setInData(['listView', 'nextPageLink'], nextPageLink);

        this.emitChange();
      }).catch(this.onListError);
    }
  },

  onOpenContainers: function(queryOptions, forceReload, keepContext) {
    var items = utils.getIn(this.data, ['listView', 'items']);
    if (!forceReload && items) {
      return;
    }

    this.setInData(['listView', 'queryOptions'], queryOptions);
    this.setInData(['selectedItem'], null);
    this.setInData(['creatingResource'], null);
    this.setInData(['selectedItemDetails'], null);

    if (!keepContext) {
      this.setInData(['contextView'], {});
    }

    var operation =
          this.requestCancellableOperation(constants.CONTAINERS.OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(constants.CONTAINERS.OPERATION.DETAILS,
        constants.CONTAINERS.OPERATION.STATS, constants.CONTAINERS.OPERATION.LOGS);
      this.setInData(['listView', 'itemsLoading'], true);
      this.setInData(['listView', 'error'], null);

      queryOptions = queryOptions || {
        $category: constants.CONTAINERS.SEARCH_CATEGORY.CONTAINERS
      };
      if (!queryOptions.$category) {
        queryOptions = $.extend({}, queryOptions);
        queryOptions.$category = constants.CONTAINERS.SEARCH_CATEGORY.CONTAINERS;
        this.setInData(['listView', 'queryOptions'], queryOptions);
        this.emitChange();
      }

      let loadResourceFunction;

      switch (queryOptions.$category) {
        case constants.RESOURCES.SEARCH_CATEGORY.NETWORKS:
          loadResourceFunction = services.loadNetworks;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.VOLUMES:
          loadResourceFunction = services.loadVolumes;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.APPLICATIONS:
          loadResourceFunction = services.loadCompositeComponents;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.CLOSURES:
          loadResourceFunction = services.loadClosures;
          break;

        default:
        case constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS:
          loadResourceFunction = services.loadContainers;
          break;
      }
      operation.forPromise(loadResourceFunction(queryOptions)).then((result) => {
        return this.decorateContainers(result, queryOptions.$category, false);
      });
    }

    this.emitChange();
  },

  onOpenContainersNext: function(queryOptions, nextPageLink) {
    this.setInData(['listView', 'queryOptions'], queryOptions);

    var operation =
          this.requestCancellableOperation(constants.CONTAINERS.OPERATION.LIST, queryOptions);

    if (operation) {
      this.setInData(['listView', 'itemsLoading'], true);
      this.setInData(['listView', 'error'], null);

      queryOptions = queryOptions || {
        $category: constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS
      };

      operation.forPromise(services.loadNextPage(nextPageLink))
        .then((result) => {

          return this.decorateContainers(result, queryOptions.$category, true);
        });
    }

    this.emitChange();
  },

  onCloseContainers: function() {
    this.setInData(['listView', 'items'], []);
    this.setInData(['listView', 'itemsLoading'], false);
    this.setInData(['listView', 'itemsCount'], 0);
    this.setInData(['listView', 'nextPageLink'], null);
    this.setInData(['listView', 'hosts'], null);
    this.setInData(['listView', 'error'], null);
  },

  onOpenManageContainers: function(containerId) {
    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.MANAGE);

    if (operation) {
      operation.forPromise(services.manageContainer(containerId))
        .then((catalogResource) => {
          if (catalogResource && catalogResource.id) {
            redirectToCatalogItem(catalogResource.id);
          }
        });
    }
  },

  onOpenManageComposite: function(catalogResourceId) {
    redirectToCatalogItem(catalogResourceId);
  },

  onOpenManageNetworks: function(networkId) {
    var operation = this.requestCancellableOperation(constants.RESOURCES.NETWORKS.OPERATION.MANAGE);

    if (operation) {
      operation.forPromise(services.manageNetwork(networkId))
        .then((catalogResource) => {
          if (catalogResource && catalogResource.id) {
            redirectToCatalogItem(catalogResource.id);
          }
        });
    }
  },

  onOpenContainerDetails: function(containerId, clusterId, compositeComponentId) {
    this.cancelOperations(constants.CONTAINERS.OPERATION.DETAILS,
      constants.CONTAINERS.OPERATION.STATS, constants.CONTAINERS.OPERATION.LOGS);

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.DETAILS);

    if (compositeComponentId) {
      this.loadCompositeComponent(compositeComponentId, operation);
    }
    if (clusterId) {
      this.loadCluster(clusterId, compositeComponentId, operation);
    }

    this.loadContainer(containerId, clusterId, compositeComponentId, operation);
  },

  onOpenClosureDetails: function(closureId, closureDescriptionId) {
    this.cancelOperations(constants.CLOSURES.OPERATION.DETAILS);
    var operation = this.requestCancellableOperation(constants.CLOSURES.OPERATION.DETAILS);

    if (closureDescriptionId) {
      this.loadCompositeClosure(closureDescriptionId, operation);
    }

    this.loadClosure(closureId, operation);
  },

  onOpenClusterDetails: function(clusterId, compositeComponentId) {

    this.cancelOperations(constants.CONTAINERS.OPERATION.DETAILS,
      constants.CONTAINERS.OPERATION.STATS, constants.CONTAINERS.OPERATION.LOGS);

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.DETAILS);

    if (compositeComponentId) {
      this.loadCompositeComponent(compositeComponentId, operation);
    }

    this.loadCluster(clusterId, compositeComponentId, operation, true);
  },

  onOpenCompositeContainerDetails: function(compositeComponentId) {
    this.cancelOperations(constants.CONTAINERS.OPERATION.DETAILS,
      constants.CONTAINERS.OPERATION.STATS, constants.CONTAINERS.OPERATION.LOGS);

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.DETAILS);

    this.loadCompositeComponent(compositeComponentId, operation, true);
  },

  onOpenCompositeClosureDetails: function(closureDescriptionId) {

    var operation = this.requestCancellableOperation(constants.CLOSURES.OPERATION.DETAILS);

    this.loadCompositeClosure(closureDescriptionId, operation, true);
  },

  onOpenCreateContainer: function() {
    this.setInData(['creatingResource'], {});
    this.setInData(['listView', 'queryOptions', '$category'],
                   constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS);
    services.loadDeploymentPolicies().then((policies) => {
      this.setInData(['creatingResource', 'definitionInstance'],
        {
          deploymentPolicies: policies
        });
      this.emitChange();
    });
  },

  onOpenCreateClosure: function() {
    this.setInData(['creatingResource'], {});
    this.setInData(['creatingResource', 'tasks'], {});
    this.setInData(['listView', 'queryOptions', '$category'],
                   constants.RESOURCES.SEARCH_CATEGORY.CLOSURES);

    Promise.all([
          services.loadPlacements()
    ]).then((placementsResult) => {
          let placements = Object.values(placementsResult[0]);
          this.setInData(['creatingResource', 'placements'], placements);
          this.emitChange();
        });

    this.emitChange();
  },

  openCreateNetwork: function() {
    this.setInData(['creatingResource'], {});
    this.setInData(['listView', 'queryOptions', '$category'],
                   constants.RESOURCES.SEARCH_CATEGORY.NETWORKS);
    this.emitChange();
  },

  onOpenCreateVolume: function() {
    this.setInData(['creatingResource'], {});
    this.setInData(['listView', 'queryOptions', '$category'],
                      constants.RESOURCES.SEARCH_CATEGORY.VOLUMES);
    this.emitChange();
  },

  onCreateContainer: function(containerDescription, group) {
    services.createContainer(containerDescription, group).then((request) => {
      this.navigateContainersListViewAndOpenRequests(request);
    }).catch(this.onGenericCreateError);
  },

  onCreateNetwork: function(networkDescription, hostIds) {
    services.createNetwork(networkDescription, hostIds).then((request) => {
      this.navigateContainersListViewAndOpenRequests(request);
    }).catch(this.onGenericCreateError);
  },

  onCreateClosure: function(closureDescription) {
    services.createClosure(closureDescription).then(() => {
        this.navigateToContainersListView();
    }).catch(this.onGenericCreateError);
  },

  onCreateVolume: function(volumeDescription, hostIds) {
    services.createVolume(volumeDescription, hostIds).then((request) => {
      // show volumes view and open requests panel
      this.navigateContainersListViewAndOpenRequests(request);

    }).catch(this.onGenericCreateError);
  },

  onRefreshContainer: function() {
    var selectedContainerDetails = getSelectedContainerDetailsCursor.call(this).get();

    if (!selectedContainerDetails || !selectedContainerDetails.documentId) {
      return;
    }

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.DETAILS);

    if (operation) {
      operation.forPromise(services.loadContainer(selectedContainerDetails.documentId))
        .then((container) => {
          enhanceContainer(container);
          updateSelectedContainerDetails.call(this, ['instance'], container);
          this.emitChange();

          services.loadHostByLink(container.parentLink).then((host) => {
            decorateContainerHostName(container, [host]);

            updateSelectedContainerDetails.call(this, ['instance'], container);
            this.emitChange();
          });
        });
    }
  },

  onRefreshContainerStats: function() {
    var selectedContainerDetails = getSelectedContainerDetailsCursor.call(this).get();

    if (!selectedContainerDetails || !selectedContainerDetails.documentId) {
      return;
    }

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.STATS);
    if (operation) {
      operation.forPromise(services.loadContainerStats(selectedContainerDetails.documentId))
        .then((stats) => {
          updateSelectedContainerDetails.call(this, ['statsLoading'], false);
          updateSelectedContainerDetails.call(this, ['stats'], stats);
          this.emitChange();
        });
    }
  },

  onRefreshContainerLogs: function() {
    var selectedContainerDetails = getSelectedContainerDetailsCursor.call(this).get();

    if (!selectedContainerDetails || !selectedContainerDetails.documentId) {
      return;
    }

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.LOGS);
    if (operation) {
      operation.forPromise(services.loadContainerLogs(selectedContainerDetails.documentId,
          selectedContainerDetails.logsSettings.sinceDuration))
        .then((logs) => {
          updateSelectedContainerDetails.call(this, ['logsLoading'], false);
          updateSelectedContainerDetails.call(this, ['logs'], logs);
          this.emitChange();
        });
    }
  },

  onChangeLogsSinceDuration: function(durationMs) {
    updateSelectedContainerDetails.call(this, ['logsSettings', 'sinceDuration'], durationMs);
    localStorage.logsSinceDuration = durationMs;
    this.emitChange();
  },

  onChangeLogsFormat: function(format) {
    updateSelectedContainerDetails.call(this, ['logsSettings', 'format'], format);
    localStorage.logsFormat = format;
    this.emitChange();
  },

  onStartContainer: function(containerId) {

    services.startContainer(containerId)
      .then((startContainerRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(startContainerRequest);
      });
  },

  onStopContainer: function(containerId) {

    services.stopContainer(containerId)
      .then((stopContainerRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(stopContainerRequest);
      });
  },

  onRemoveContainer: function(containerId) {

    services.removeContainer(containerId)
      .then((removalRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(removalRequest);
      });
  },


  onRemoveClosureRun: function(closureRunlink) {
    services.deleteClosureRun(closureRunlink);
  },

  onRemoveNetwork: function(networkId) {

    services.removeNetwork(networkId)
      .then((removalRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(removalRequest);
      });
  },

  onRemoveVolume: function(volumeId) {

    services.removeVolume(volumeId)
      .then((removalRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(removalRequest);
      });
  },

  onStartCompositeContainer: function(compositeId) {

    services.startCompositeContainer(compositeId)
      .then((startContainerRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(startContainerRequest);
      });
  },

  onStopCompositeContainer: function(compositeId) {

    services.stopCompositeContainer(compositeId)
      .then((stopContainerRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(stopContainerRequest);
      });
  },

  onRemoveCompositeContainer: function(compositeId) {

    services.removeCompositeContainer(compositeId)
      .then((removalRequest) => {

        this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
        actions.RequestsActions.requestCreated(removalRequest);
      });
  },

  onStartCluster: function(clusterContainers) {
    services.startCluster(clusterContainers).then((startClusterRequest) => {

      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(startClusterRequest);
    });
  },

  onStopCluster: function(clusterContainers) {
    services.stopCluster(clusterContainers).then((stopClusterRequest) => {

      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(stopClusterRequest);
    });
  },

  onRemoveCluster: function(clusterContainers) {
    services.removeCluster(clusterContainers).then((removalRequest) => {

      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(removalRequest);
    });
  },

  onStartContainerDetails: function(containerId) {

    this.clearOperationFailure();

    services.startContainer(containerId)
      .then((startContainerRequest) => {
        var cursor = getSelectedContainerDetailsCursor.call(this);
        if (cursor.getIn(['documentId']) === containerId) {
          cursor.setIn(['operationInProgress'], constants.CONTAINERS.OPERATION.START);
        }
        this.emitChange();

        actions.RequestsActions.requestCreated(startContainerRequest);
      });
  },

  onStopContainerDetails: function(containerId) {

    this.clearOperationFailure();

    services.stopContainer(containerId)
      .then((stopContainerRequest) => {
        var cursor = getSelectedContainerDetailsCursor.call(this);
        if (cursor.getIn(['documentId']) === containerId) {
          cursor.setIn(['operationInProgress'], constants.CONTAINERS.OPERATION.STOP);
        }
        this.emitChange();

        actions.RequestsActions.requestCreated(stopContainerRequest);
      });
  },

  onRemoveContainerDetails: function(containerId) {

    this.clearOperationFailure();

    services.removeContainer(containerId)
      .then((removalRequest) => {
        var cursor = getSelectedContainerDetailsCursor.call(this);
        if (cursor.getIn(['documentId']) === containerId) {
          cursor.setIn(['operationInProgress'], constants.CONTAINERS.OPERATION.REMOVE);
        }
        this.emitChange();

        actions.RequestsActions.requestCreated(removalRequest);
      });
  },

  onBatchOpContainers: function(containerLinks, operation) {
    services.batchOpContainers(containerLinks, operation).then((batchOpRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());

      actions.RequestsActions.requestCreated(batchOpRequest);
    });
  },

  onBatchOpClosures: function(closureLinks, operation) {
    services.batchOpClosures(closureLinks, operation).then((batchOpRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());

      actions.RequestsActions.requestCreated(batchOpRequest);
    });
  },

  onBatchOpCompositeContainers: function(compositeIds, operation) {
    services.batchOpCompositeContainers(compositeIds, operation).then((batchOpRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());

      actions.RequestsActions.requestCreated(batchOpRequest);
    });
  },

  onBatchOpNetworks: function(networkLinks, operation) {
    services.batchOpNetworks(networkLinks, operation).then((batchOpRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());

      actions.RequestsActions.requestCreated(batchOpRequest);
    });
  },

  onBatchOpVolumes: function(volumeLinks, operation) {
    services.batchOpVolumes(volumeLinks, operation).then((batchOpRequest) => {
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());

      actions.RequestsActions.requestCreated(batchOpRequest);
    });
  },

  backFromContainerAction: function(operationType, resourceIds) {
    var cursor = getSelectedContainerDetailsCursor.call(this);

    if (cursor && resourceIds.length === 1
          && cursor.getIn(['documentId']) === resourceIds[0]
          && cursor.getIn(['operationInProgress'])) {
      // Refresh Container Details
      cursor.setIn(['operationInProgress'], null);

      if (operationType !== constants.CONTAINERS.OPERATION.REMOVE) {

        actions.ContainerActions.refreshContainer();
      } else {
        return this.refreshView(cursor.parent(), true, operationType, resourceIds);
      }
    } else {
      var lastSelectedItemDetailsCursor = getSelectedItemDetailsCursor.call(this);

      return this.refreshView(lastSelectedItemDetailsCursor, false, operationType, resourceIds);
    }
  },

  refreshView: function(selectedItemDetailsCursor, navigateToParent, operationType, resourceIds) {

    if ((selectedItemDetailsCursor != null) && (selectedItemDetailsCursor.get() != null)) {

      return this.showSelectedDetailsView(selectedItemDetailsCursor, navigateToParent,
                                            operationType, resourceIds);
    } else {
      // Refresh Containers List view if nothing has been selected
      this.navigateToContainersListView(navigateToParent);
    }
  },

  showSelectedDetailsView(selectedItemDetailsCursor, navigateToParent, operationType,
                          resourceIds) {
    let selectedItemDetails = selectedItemDetailsCursor.get();

    if (isEverythingRemoved.call(this, selectedItemDetails, operationType, resourceIds)) {

      if (selectedItemDetails.type === constants.CONTAINERS.TYPES.COMPOSITE) {
        this.navigateToContainersListView(true);

      } else if (selectedItemDetails.type === constants.CONTAINERS.TYPES.CLUSTER) {
        let parentCursor = selectedItemDetailsCursor.parent();
        let parentDetails = (parentCursor != null) ? parentCursor.get() : null;

        if (parentDetails && parentDetails.type === constants.CONTAINERS.TYPES.COMPOSITE) {
          let isTheOnlyElemInComposite = (parentDetails.listView.items.length === 1);

          if (isTheOnlyElemInComposite) {
            this.navigateToContainersListView(true);
          } else {
            actions.NavigationActions.openCompositeContainerDetails(parentDetails.documentId);
          }
        }
      } else {
        return this.navigateToContainersListView(true);
      }
    } else {

      return this.showLastShownView(selectedItemDetailsCursor, navigateToParent);
    }
  },

  showLastShownView(selectedItemDetailsCursor, navigateToParent) {
    let selectedItemDetails = selectedItemDetailsCursor.get();

    // Refresh the last shown view of a selected item
    if (selectedItemDetails.type === constants.CONTAINERS.TYPES.CLUSTER) {
      let clusterId = selectedItemDetails.documentId;
      let parentCursor = selectedItemDetailsCursor.parent();
      let compositeContainerId = (parentCursor != null) ? parentCursor.get().documentId : null;

      if (navigateToParent) {
        actions.NavigationActions.openClusterDetails(clusterId, compositeContainerId);
      } else {
        actions.ContainerActions.openClusterDetails(clusterId, compositeContainerId);
      }

    } else if (selectedItemDetails.type === constants.CONTAINERS.TYPES.COMPOSITE) {

      if (navigateToParent) {
        actions.NavigationActions.openCompositeContainerDetails(selectedItemDetails.documentId);
      } else {
        actions.ContainerActions.openCompositeContainerDetails(selectedItemDetails.documentId);
      }

    } else if (selectedItemDetails.type === constants.CONTAINERS.TYPES.SINGLE) {
      this.navigateToContainersListView(navigateToParent);

    } else if (selectedItemDetails.listView) {
      this.navigateToContainersListView(navigateToParent);
    }
  },

  navigateToContainersListView: function(navigateToParent) {
    var queryOptions = utils.getIn(this.data, ['listView', 'queryOptions']);

    if (navigateToParent) {
      actions.NavigationActions.openContainers(queryOptions);
    } else {
      actions.ContainerActions.openContainers(queryOptions, true, true);
    }
  },

  navigateContainersListViewAndOpenRequests: function(request) {
    var queryOptions = utils.getIn(this.data, ['listView', 'queryOptions']);

    var openContainersUnsubscribe = actions.ContainerActions.openContainers.listen(() => {
      openContainersUnsubscribe();
      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(request);
    });

    actions.NavigationActions.openContainers(queryOptions);
  },

  onOperationCompleted: function(operationType, resourceIds) {
    if (operationType === constants.CONTAINERS.OPERATION.START) {
      // Container Started
      this.backFromContainerAction(operationType, resourceIds);

    } else if (operationType === constants.CONTAINERS.OPERATION.STOP) {
      // Container Stopped
      this.backFromContainerAction(operationType, resourceIds);

    } else if (operationType === constants.CONTAINERS.OPERATION.CREATE) {
      // Container created
    } else if (operationType === constants.CONTAINERS.OPERATION.REMOVE) {
      // Container Removed
      this.backFromContainerAction(operationType, resourceIds);

    } else if (operationType === constants.CONTAINERS.OPERATION.CLUSTERING) {
      //  Clustering day2 op
      this.backFromContainerAction(operationType, resourceIds);

    } else if (operationType === constants.CONTAINERS.OPERATION.DEFAULT) {
      // default - refresh current view on operation finished
      this.backFromContainerAction(operationType, resourceIds);
    } else if (operationType === constants.CONTAINERS.OPERATION.NETWORKCREATE) {
      // Network created
      this.backFromContainerAction(operationType, resourceIds);
    } else if (operationType === constants.CONTAINERS.OPERATION.CREATE_VOLUME) {
      // Volume created
      this.backFromContainerAction(operationType, resourceIds);
    }
  },

  onOperationFailed: function(operationType, resourceIds) {
    // Currently only needed for the Details view
    var cursor = getSelectedContainerDetailsCursor.call(this);
    if (cursor && cursor.getIn(['documentId']) === resourceIds[0] &&
      cursor.getIn(['operationInProgress'])) {
      cursor.setIn(['operationInProgress'], null);
      cursor.setIn(['operationFailure'], operationType);

      actions.ContainerActions.refreshContainer();
    } else {
      return this.refreshView(cursor, false, operationType, resourceIds);
    }
  },

  onNetworkOperationCompleted: function(operationType) {
    if (operationType === constants.RESOURCES.NETWORKS.OPERATION.REMOVE) {
      this.navigateToContainersListView(false);
    }
  },

  onNetworkOperationFailed: function(operationType) {
    if (operationType === constants.RESOURCES.NETWORKS.OPERATION.REMOVE) {
      this.navigateToContainersListView(false);
    }
  },

  onVolumeOperationCompleted: function(operationType) {
    if (operationType === constants.RESOURCES.VOLUMES.OPERATION.REMOVE) {
      this.navigateToContainersListView(false);
    }
  },

  onVolumeOperationFailed: function(operationType) {
    if (operationType === constants.RESOURCES.VOLUMES.OPERATION.REMOVE) {
      this.navigateToContainersListView(false);
    }
  },

  clearOperationFailure: function() {
    this.setInData(['selectedItemDetails', 'operationFailure'], null);
  },

  selectComponent: function(containerId, clusterId, compositeComponentId) {
    var cursor = this.selectFromData([]);

    if (compositeComponentId) {
      cursor = cursor.select(['selectedItemDetails']);
      let value = cursor.get();
      if (!value || value.type !== constants.CONTAINERS.TYPES.COMPOSITE ||
        value.documentId !== compositeComponentId) {
        return null;
      }
    }

    if (clusterId) {
      cursor = cursor.select(['selectedItemDetails']);
      let value = cursor.get();
      if (!value || value.type !== constants.CONTAINERS.TYPES.CLUSTER ||
        value.documentId !== clusterId) {
        return null;
      }
    }

    if (containerId) {
      cursor = cursor.select(['selectedItemDetails']);
      let value = cursor.get();
      if (!value || value.type !== constants.CONTAINERS.TYPES.SINGLE ||
        value.documentId !== containerId) {
        return null;
      }
    }

    return cursor;
  },


  loadCompositeClosure: function(closureDescriptionId, operation, force) {
    var parentCursor = this.selectComponent(null, null, null);

    var currentCompositeComponent = parentCursor.select(['selectedItemDetails']).get();

    if (currentCompositeComponent &&
        currentCompositeComponent.documentId === closureDescriptionId && !force) {
      return;
    }

    var compositeClosure = {
      documentId: closureDescriptionId,
      name: closureDescriptionId,
      type: constants.RESOURCES.TYPES.CLOSURE_DESC
    };

    var compositeClosureDetails = {
      documentId: closureDescriptionId,
      type: constants.RESOURCES.TYPES.CLOSURE_DESC,
      listView: {
        itemsLoading: true
      }
    };
    if (currentCompositeComponent &&
        currentCompositeComponent.documentId === closureDescriptionId) {
      compositeClosureDetails.listView.items = currentCompositeComponent.listView.items;
    }

    parentCursor.setIn(['selectedItem'], compositeClosure);
    parentCursor.setIn(['selectedItemDetails'], compositeClosureDetails);
    // clear errors
    parentCursor.setIn(['selectedItemDetails', 'error'], null);

    this.emitChange();

    var closureDescriptionLink = links.CLOSURE_DESCRIPTIONS + '/' + closureDescriptionId;

    operation.forPromise(Promise.all([
      services.loadClosureDescriptionById(closureDescriptionId),
      services.loadClosureRuns(closureDescriptionLink)
    ])).then(([retrievedClosureDescription, fecthedClosureResult]) => {

      var childClosures = utils.resultToArray(fecthedClosureResult.documents ?
          fecthedClosureResult.documents : fecthedClosureResult);

      enhanceClosureDesc(retrievedClosureDescription);

      parentCursor.select(['selectedItem']).merge(retrievedClosureDescription);
      parentCursor.select(['selectedItemDetails']).merge(retrievedClosureDescription);

      childClosures.forEach((childClosure) => {
        enhanceClosure(childClosure);
      });

      var items = childClosures;

      parentCursor.select(['selectedItemDetails', 'listView'])
        .setIn(['items'], items)
        .setIn(['itemsLoading'], false);

      this.emitChange();

      parentCursor.select(['selectedItemDetails'])
        .setIn(['listView', 'items'], items);
      this.emitChange();
    }).catch(this.onGenericDetailsError);
  },

  loadCompositeComponent: function(compositeComponentId, operation, force) {
    var parentCursor = this.selectComponent(null, null, null);

    var currentCompositeComponent = parentCursor.select(['selectedItemDetails']).get();

    if (currentCompositeComponent &&
        currentCompositeComponent.documentId === compositeComponentId && !force) {
      return;
    }

    var compositeComponent = {
      documentId: compositeComponentId,
      name: compositeComponentId,
      type: constants.CONTAINERS.TYPES.COMPOSITE
    };

    var compositeComponentDetails = {
      documentId: compositeComponentId,
      type: constants.CONTAINERS.TYPES.COMPOSITE,
      listView: {
        itemsLoading: true
      }
    };

    if (currentCompositeComponent &&
        currentCompositeComponent.documentId === compositeComponentId) {
      compositeComponentDetails.listView.items = currentCompositeComponent.listView.items;
      compositeComponentDetails.listView.networks = currentCompositeComponent.listView.networks;
      compositeComponentDetails.listView.networkLinks =
                                                  currentCompositeComponent.listView.networkLinks;
    }

    parentCursor.setIn(['selectedItem'], compositeComponent);
    parentCursor.setIn(['selectedItemDetails'], compositeComponentDetails);
    // clear errors
    parentCursor.setIn(['selectedItemDetails', 'error'], null);

    this.emitChange();

    operation.forPromise(Promise.all([
      services.loadCompositeComponent(compositeComponentId),
      services.loadContainersForCompositeComponent(compositeComponentId),
      services.loadNetworksForCompositeComponent(compositeComponentId)
    ])).then(([retrievedCompositeComponent, childContainersResult, childNetworksResult]) => {

      var childContainers = utils.resultToArray(childContainersResult.documents ?
          childContainersResult.documents : childContainersResult);

      var childNetworks = utils.resultToArray(childNetworksResult.documents ?
          childNetworksResult.documents : childNetworksResult);

      enhanceCompositeComponent(retrievedCompositeComponent);
      retrievedCompositeComponent.icons = getContainersImageIcons(childContainers);
      parentCursor.select(['selectedItem']).merge(retrievedCompositeComponent);
      parentCursor.select(['selectedItemDetails']).merge(retrievedCompositeComponent);

      childContainers.forEach((childContainer) => {
        enhanceContainer(childContainer);
      });

      var items = this.aggregateClusterNodes(childContainers);

      var networkLinks = getNetworkLinks(items, childNetworks);

      parentCursor.select(['selectedItemDetails', 'listView'])
        .setIn(['items'], items)
        .setIn(['itemsLoading'], false)
        .setIn(['networks'], childNetworks)
        .setIn(['networkLinks'], networkLinks);

      this.emitChange();

      this.getHostsForContainersCall(childContainers).then((hosts) => {
        childContainers.forEach((childContainer) => {
          decorateContainerHostName(childContainer, utils.resultToArray(hosts));
        });

        parentCursor.select(['selectedItemDetails'])
          .setIn(['listView', 'items'], items);
        this.emitChange();
      });

    }).catch(this.onGenericDetailsError);
  },

  loadCluster: function(clusterId, compositeComponentId, operation, force) {
    var parentCursor = this.selectComponent(null, null, compositeComponentId);

    var currentClusterComponent = parentCursor.select(['selectedItemDetails']).get();

    if (currentClusterComponent && currentClusterComponent.documentId === clusterId && !force) {
      return;
    }

    var clusterComponent = makeClusterObject(clusterId);

    var clusterComponentDetails = {
      documentId: clusterComponent.documentId,
      descriptionLink: clusterComponent.descriptionLink,
      type: constants.CONTAINERS.TYPES.CLUSTER,

      listView: {
        itemsLoading: true
      }
    };

    if (currentClusterComponent && currentClusterComponent.documentId === clusterId) {
      clusterComponentDetails.listView.items = currentClusterComponent.listView.items;
    }

    parentCursor.setIn(['selectedItem'], clusterComponent);
    parentCursor.setIn(['selectedItemDetails'], clusterComponentDetails);
    this.emitChange();

    // query containers by description link
    operation.forPromise(
      services.loadClusterContainers(
        getDescriptionLinkFromClusterId(clusterId), compositeComponentId))
      .then((containers) => {

        if (parentCursor.get().type === constants.CONTAINERS.TYPES.COMPOSITE) {
          parentCursor.setIn(['expanded', true]);
        }
        var items = utils.resultToArray(containers);
        items.forEach((container) => {
          enhanceContainer(container, clusterId);
        });

        let cluster = makeClusterObject(clusterId, items);

        parentCursor.setIn(['selectedItem'], cluster);
        parentCursor.select(['selectedItemDetails'])
          .setIn(['listView', 'items'], items)
          .setIn(['listView', 'itemsLoading'], false);

        this.emitChange();

        this.getHostsForContainersCall(items).then((hosts) => {
          items.forEach((container) => {
            decorateContainerHostName(container, utils.resultToArray(hosts));
          });
          parentCursor.select(['selectedItemDetails']).setIn(['listView', 'items'], items);
          this.emitChange();
        });
      }).catch(this.onGenericDetailsError);
  },

  loadContainer: function(containerId, clusterId, compositeComponentId, operation) {
    var parentCursor = this.selectComponent(null, clusterId, compositeComponentId);
    // If switching between views, there will be a short period that we show old data,
    // until the new one is loaded.
    var currentItemDetailsCursor = parentCursor.select(['selectedItemDetails']);
    currentItemDetailsCursor.merge({
      logsSettings: {
        sinceDuration: localStorage.logsSinceDuration
          || constants.CONTAINERS.LOGS.SINCE_DURATIONS[0],
        format: localStorage.logsFormat || constants.CONTAINERS.LOGS.FORMAT.ANSI
      },
      type: constants.CONTAINERS.TYPES.SINGLE,
      documentId: containerId,
      logsLoading: true,
      statsLoading: true,
      templateLink: null,
      descriptionLinkToConvertToTemplate: null
    });

    var currentItemCursor = parentCursor.select(['selectedItem']);
    currentItemCursor.merge({
      type: constants.CONTAINERS.TYPES.SINGLE,
      documentId: containerId
    });
    this.emitChange();

    operation.forPromise(services.loadContainer(containerId))
      .then((container) => {
        enhanceContainer(container);

        if (parentCursor.get().type === constants.CONTAINERS.TYPES.CLUSTER ||
          parentCursor.get().type === constants.CONTAINERS.TYPES.COMPOSITE) {
          parentCursor.setIn(['expanded', true]);
        }

        currentItemCursor.merge(container);
        currentItemDetailsCursor.setIn(['instance'], container);
        this.emitChange();

        this.loadTemplateContainerDescription(container.descriptionLink).then((template) => {
          if (currentItemDetailsCursor.getIn(['documentId']) === containerId) {
            if (template) {
              currentItemDetailsCursor.setIn(['templateLink'], template.documentSelfLink);
            } else {
              currentItemDetailsCursor.setIn(['descriptionLinkToConvertToTemplate'],
                container.descriptionLink);
            }

            this.emitChange();
          }
        });

        services.loadHostByLink(container.parentLink).then((host) => {
          decorateContainerHostName(container, [host]);
          currentItemCursor.merge(container);
          currentItemDetailsCursor.setIn(['instance'], container);
          this.emitChange();
        });
      }).catch(this.onGenericDetailsError);
  },

  loadTemplateContainerDescription: function(descriptionLink) {
    return this.loadTopMostParentDescription(descriptionLink).then((description) => {
      if (description.documentSelfLink === descriptionLink) {
        return null;
      } else {
        return services.loadTemplatesContainingComponentDescriptionLink(
          description.documentSelfLink).then((result) => {
            if (!result) {
              return;
            }

            var template;
            for (var key in result) {
              if (result.hasOwnProperty(key)) {
                if (template) {
                  console.info(
                    'More than one template found for a given description, will show only first.');
                } else {
                  template = result[key];
                }
              }
            }

            return template;
        });
      }
    });
  },

  loadTopMostParentDescription: function(descriptionLink) {
    return services.loadDocument(descriptionLink).then((description) => {
      if (description.parentDescriptionLink) {
        return this.loadTopMostParentDescription(description.parentDescriptionLink);
      } else {
        return description;
      }
    });
  },

  onCreateTemplateFromContainer: function(container) {
    var cursor = getSelectedContainerDetailsCursor.call(this);
    if (!cursor || cursor.getIn(['documentId']) !== container.documentId) {
      return;
    }

    cursor.setIn(['operationInProgress'], constants.CONTAINERS.OPERATION.CREATE_TEMPLATE);
      this.emitChange();

    services.createContainerTemplateForDescription(container.names[0], container.descriptionLink)
    .then((template) => {
      return services.copyContainerTemplate(template);
    }).then((copyTemplate) => {
      return services.patchDocument(container.documentSelfLink, {
        descriptionLink: copyTemplate.descriptionLinks[0]
      }).then(() => {
        if (cursor.getIn(['documentId']) !== container.documentId) {
          return;
        }

        cursor.setIn(['operationInProgress'], null);
        this.emitChange();

        var templateId = utils.getDocumentId(copyTemplate.parentDescriptionLink);

        actions.NavigationActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE,
          templateId);
      });
    }).catch(this.onGenericDetailsError);
  },

  loadClosure: function(closureId, operation) {
    var parentCursor = this.selectComponent(null, null, null);
    // If switching between views, there will be a short period that we show old data,
    // until the new one is loaded.

    var currentItemDetailsCursor = parentCursor.select(['selectedItemDetails']);
    currentItemDetailsCursor.merge({
      type: constants.RESOURCES.TYPES.CLOSURE,
      documentId: closureId,
      logsLoading: true,
      statsLoading: true
    });

    var currentItemCursor = parentCursor.select(['selectedItem']);
    currentItemCursor.merge({
      type: constants.RESOURCES.TYPES.CLOSURE,
      documentId: closureId
    });
    this.emitChange();

    operation.forPromise(services.loadClosure(closureId))
      .then((closure) => {
        services.loadClosureDescription(closure.descriptionLink).then((closureDescription) => {
          enhanceClosureWithDescription(closure, closureDescription);
          currentItemCursor.merge(closure);
          currentItemDetailsCursor.setIn(['instance'], closure);
          this.emitChange();
        });
      }).catch(this.onGenericDetailsError);
  },

  aggregateClusterNodes: function(nodes) {
    let nodesByClusterId = [];
    for (let n in nodes) {

      if (!nodes.hasOwnProperty(n)) {
        continue;
      }

      let node = nodes[n];

      let compositeContextId = node.customProperties
                        ? node.customProperties.__composition_context_id : null;

      let clusterId = makeClusterId(node.descriptionLink, compositeContextId);

      let nodeGroup = nodesByClusterId[clusterId];
      if (!nodeGroup) {
        nodeGroup = [];
        nodesByClusterId[clusterId] = nodeGroup;
      }
      nodeGroup.push(node);
    }

    var items = [];
    for (let key in nodesByClusterId) {

      if (!nodesByClusterId.hasOwnProperty(key)) {
        continue;
      }

      let containerGroup = nodesByClusterId[key];
      if (key.endsWith('__discovered__')) { // we cannot restore cluster info in this case
        items = items.concat(containerGroup);
      } else {
        let clusterSize = containerGroup.length;
        let firstContainer = containerGroup[0];
        if (clusterSize > 1) { // cluster
          items.push(makeClusterObject(key, containerGroup));
        } else {
          items.push(firstContainer);
        }
      }
    }

    return items;
  },

  onModifyClusterSize: function(clusterId, totalClusterSize) {
    let descriptionLink = getDescriptionLinkFromClusterId(clusterId);
    let contextId = getContextIdFromClusterId(clusterId);

    services.modifyClusterSize(descriptionLink, contextId, totalClusterSize)
        .then((clusterSizeRequest) => {

      this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
      actions.RequestsActions.requestCreated(clusterSizeRequest);
    });
  },

  onScaleContainer: function(descriptionLink, contextId) {
    var clusterId = makeClusterId(descriptionLink, contextId);

    // also handles the case when the container is already in cluster
    services.loadClusterContainers(descriptionLink, contextId).then((containers) => {
      let clusterSize = getClusterSize(containers);

      return this.onModifyClusterSize(clusterId, clusterSize + 1);
    });
  },

  onOpenToolbarEventLogs: function(highlightedItemLink) {
    actions.EventLogActions.openEventLog(highlightedItemLink);
    this.openToolbarItem(constants.CONTEXT_PANEL.EVENTLOGS, EventLogStore.getData());
  },

  onOpenToolbarRequests: function() {
    actions.RequestsActions.openRequests();
    this.openToolbarItem(constants.CONTEXT_PANEL.REQUESTS, RequestsStore.getData());
  },

  onOpenToolbarClosureResults: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.CLOSURES, null, false);
  },
  createClosure: function(closureDescription) {
    return services.createClosure(closureDescription);
  },

  saveClosure: function(closureDescription) {
    if (!closureDescription.documentSelfLink) {
      this.createClosure(closureDescription)
        .then(() => {
          this.navigateToContainersListView(true);
        }).catch(this.onGenericEditError);
    } else {
      services.editClosure(closureDescription)
        .then(() => {
          this.navigateToContainersListView(true);
        }).catch(this.onGenericEditError);
    }
  },

  runClosure: function(closureDescription, inputs) {
    var createRun = (closureDescription) => {
      return services.runClosure(closureDescription, inputs).then((request) => {
        this.setInData(['tasks', 'monitoredTask'], request);
        this.setInData(['creatingResource', 'tasks', 'monitoredTask', 'isRunning'], {});
        this.emitChange();

        if (!this.requestCheckInterval) {
          this.requestCheckInterval = setInterval(this.refreshMonitoredTask,
            CHECK_INTERVAL_MS);
        }
      });
    };

    this.setInData(['tasks', 'monitoredTask'], null);
    this.emitChange();

    if (!closureDescription.documentSelfLink) {
      this.createClosure(closureDescription).then((createdClosure) => {
        this.setInData(['creatingResource', 'tasks', 'editingItemData', 'item'], createdClosure);
        this.emitChange();

        return createRun(createdClosure);
      }).catch(this.onGenericEditError);
    } else {
      services.editClosure(closureDescription).then(() => {
        return createRun(closureDescription);
      }).catch(this.onGenericEditError);
    }
  },
  refreshMonitoredTask: function() {
    var task = this.data.tasks.monitoredTask;
    if (task) {
      if (task.state === 'FINISHED' || task.state === 'FAILED' || task.state === 'CANCELLED') {
        this.stopTaskRefresh();
        this.fetchLogs();

        this.setInData(['creatingResource', 'tasks', 'monitoredTask', 'isRunning'], null);
        this.emitChange();
        return;
      } else {
        console.log('Monitoring closure: ' + task.documentSelfLink);
      }
      services.getClosure(task.documentSelfLink).then((fetchedTask) => {
        this.setInData(['tasks', 'monitoredTask'], fetchedTask);
        this.setInData(['tasks', 'monitoredTask', 'taskId'],
          fetchedTask.documentSelfLink.split('/').pop());
        this.emitChange();
        if (fetchedTask.resourceLinks && fetchedTask.resourceLinks.length > 0) {
          this.fetchLogs();
        }
      });
    } else {
      console.warn('No available closure to monitor!');
    }
  },
  resetMonitoredClosure: function() {
    if (this.data.tasks) {
      console.log('Resetting monitored closure...');
      this.setInData(['tasks', 'monitoredTask'], null);
      this.emitChange();
    }
  },
  stopTaskRefresh: function() {
    if (this.requestCheckInterval) {
      clearInterval(this.requestCheckInterval);
      this.requestCheckInterval = null;
    }
  },
  fetchLogs: function() {
    var task = this.data.tasks.monitoredTask;
    if (typeof task.resourceLinks === 'undefined' || task.resourceLinks.length <= 0) {
      console.log('No resources to fetch logs...');
      this.setInData(['tasks', 'monitoredTask', 'taskLogs'], task.errorMsg);
      this.emitChange();
      return;
    }

    var taskLogResource = task.resourceLinks[0].split('/').pop();
    console.log('Requesting logs from: ' + taskLogResource);
    services.getClosureLogs(taskLogResource).then((fetchedLogs) => {
      this.setInData(['tasks', 'monitoredTask', 'taskLogs'], atob(fetchedLogs.logs));
      this.emitChange();
    });
  },

  onCloseToolbar: function() {
    this.closeToolbar();
  },

  onOpenShell: function(containerId) {
    services.getContainerShellUri(containerId).then((shellUri) => {
      var path = window.location.pathname || '';
      // Remove file endings, like index.html
      path = path.substring(0, path.lastIndexOf('/') + 1);
      if (shellUri.indexOf(path) === 0) {
        shellUri = shellUri.substring(path.length);
      }
      if (shellUri.charAt(0) === '/') {
        shellUri = shellUri.substring(1);
      }
      if (shellUri.charAt(shellUri.length - 1) !== '/') {
        shellUri += '/';
      }

      var selectedContainerDetails = getSelectedContainerDetailsCursor.call(this).get();

      if (!selectedContainerDetails || containerId !== selectedContainerDetails.documentId) {
        return;
      }

      updateSelectedContainerDetails.call(this, ['shell'], {
        shellUri: shellUri
      });
      this.emitChange();
    });
  },

  onCloseShell: function() {
    updateSelectedContainerDetails.call(this, ['shell'], null);
    this.emitChange();
  },

  getHostsForContainersCall(containers) {
    let hosts = utils.getIn(this.data, ['listView', 'hosts']) || {};
    let hostLinks = containers.filter((container) =>
      container.parentLink).map((container) => container.parentLink);
    let links = [...new Set(hostLinks)].filter((link) => !hosts.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(hosts);
    }
    return services.loadHostsByLinks(links).then((newHosts) => {
      this.setInData(['listView', 'hosts'], $.extend({}, hosts, newHosts));
      return utils.getIn(this.data, ['listView', 'hosts']);
    });
  },

  onListError: function(e) {
    let errorMessage = utils.getErrorMessage(e);

    this.setInData(['listView', 'error'], errorMessage);
    this.emitChange();
  },

  onGenericDetailsError: function(e) {
    let errorMessage = utils.getErrorMessage(e);

    this.setInData(['selectedItemDetails', 'error'], errorMessage);
    this.emitChange();
  },
  onGenericCreateError: function(e) {
    let errorMessage = utils.getErrorMessage(e);

    this.setInData(['creatingResource', 'error'], errorMessage);
    this.emitChange();
  },
  // Exposed only for testing, not to be used in the actual application
  _clearData: function() {
    if (!jasmine) { // eslint-disable-line
      throw new Error('_clearData is not supported');
    }

    this.data = Immutable({});
  }
});

export default ContainersStore;
