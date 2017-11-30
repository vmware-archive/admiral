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
    container.hostPublicAddress = utils.getCustomPropertyValue(host.customProperties,
                                      constants.HOST.CUSTOM_PROPS.PUBLIC_ADDRESS) || host.address;
    container.hostDocumentId = utils.getDocumentId(host.documentSelfLink);
    container.isOnVchHost = host.customProperties.__containerHostType === constants.HOST.TYPE.VCH;
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

function enhanceProject(project) {
  project.icon = imageUtils.getImageIconLink(project.name);
  project.documentId = utils.getDocumentId(project.documentSelfLink);
  project.type = constants.RESOURCES.TYPES.PROJECT;
  return project;
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
                           .indexOf('#') + 1);
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

let getContainerVolumeLinks = function(containerVolumes, volumes) {
  var containerVolumeLinks = [];

  if (containerVolumes) {
    containerVolumes.forEach((containerVolumeString) => {
      let volume = utils.findVolume(containerVolumeString, volumes);

      if (volume) {
        containerVolumeLinks.push(volume.documentSelfLink);
      }
    });
  }

  return containerVolumeLinks;
};

let getVolumeLinks = function(compositeComponentItems, volumes) {
  var volumeLinks = {};

  function fillVolumeLinks(item) {
    let itemVolumes = item.volumes;

    if (item.containers) { // cluster
      itemVolumes = [];

      item.containers.forEach((container) => {
        if (container.volumes) {
          itemVolumes = itemVolumes.concat(container.volumes);
        }
      });
    }

    let containerVolumeLinks = getContainerVolumeLinks(itemVolumes, volumes);

    let containerSelfLink = item.documentSelfLink;
    if (!volumeLinks[containerSelfLink]) {
      volumeLinks[containerSelfLink] = [];
    }

    volumeLinks[containerSelfLink] = [...new Set(containerVolumeLinks)];
  }

  compositeComponentItems.forEach((item) => {
    fillVolumeLinks(item);
  });

  return volumeLinks;
};

let updateApplicationIcons = function(applicationItemsResult, applications) {
  let applicationItems = applicationItemsResult.documentLinks.map((documentLink) => {
    return applicationItemsResult.documents[documentLink];
  });

  if (applicationItems.length < 1) {
    return;
  }

  let appLinks = new Set();
  applicationItems.forEach(appItem => {
    appItem.compositeComponentLinks.forEach(appLink => {
      appLinks.add(appLink);
    });
  });

  let compositeComponentLinks = [...appLinks];
  let application = applications.find((item) => {
    return compositeComponentLinks.find((compositeComponentLink) => {
      return item.documentSelfLink === compositeComponentLink;
    });
  });

  if (application) {
    if (!application.icons) {
      application.icons = [];
    }

    let applicationItemsIcons = new Set();
    applicationItems.forEach(applicationItem => {
      let applicationItemIcon = imageUtils.getImageIconLink(applicationItem.name);

      if (applicationItemIcon) {
        applicationItemsIcons.add(applicationItemIcon);
      }
    });
    application.icons.push([...applicationItemsIcons]);
  }
};

let updateContainerItem = function(item, updatedItem) {
  let updated = false;

  if (updatedItem) {
    item = item.asMutable();

    if (updatedItem.powerState !== item.powerState) {
      item.powerState = updatedItem.powerState;
      updated = true;
    }

    if (updatedItem.started !== item.started) {
      item.started = updatedItem.started;
      updated = true;
    }
  }

  return updated ? item : undefined;
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
    actions.KubernetesActions,
    actions.RegistryActions,
    actions.ContainersContextToolbarActions,
    actions.ResourceGroupsActions
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
        category === constants.RESOURCES.SEARCH_CATEGORY.CLOSURES ||
        category === constants.RESOURCES.SEARCH_CATEGORY.PROJECTS) {

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
        case constants.RESOURCES.SEARCH_CATEGORY.PROJECTS:
          enhanceFunction = enhanceProject;
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
    } else { // Applications
      let compositeComponentsContainersCalls = [];

      items.forEach((item) => {
        enhanceCompositeComponent(item);

        compositeComponentsContainersCalls.push(this.loadApplication(item.documentSelfLink));
      });

      // Load containers of the current composite components
      Promise.all(compositeComponentsContainersCalls).then((result) => {
        for (let i = 0; i < result.length; i++) {

          let childContainersResult = result[i][0];
          let childNetworksResult = result[i][1];
          let childVolumesResult = result[i][2];

          let containers = childContainersResult.documentLinks.map((documentLink) => {
            return childContainersResult.documents[documentLink];
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

          // Networks
          updateApplicationIcons(childNetworksResult, items);

          // Volumes
          updateApplicationIcons(childVolumesResult, items);
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

  loadApplication: function(applicationId) {

    return Promise.all([
      services.loadContainersForCompositeComponent(applicationId),
      services.loadNetworksForCompositeComponent(applicationId),
      services.loadVolumesForCompositeComponent(applicationId)
    ]);
  },

  onOpenContainers: function(queryOptions, forceReload, keepContext) {
    this.setInData(['selectedItem'], null);
    this.setInData(['creatingResource'], null);
    this.setInData(['selectedItemDetails'], null);

    var items = utils.getIn(this.data, ['listView', 'items']);
    if (!forceReload && items) {
      this.emitChange();
      return;
    }

    this.setInData(['listView', 'queryOptions'], queryOptions);

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
          loadResourceFunction = services.loadContainerNetworks;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.VOLUMES:
          loadResourceFunction = services.loadContainerVolumes;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.APPLICATIONS:
          loadResourceFunction = services.loadCompositeComponents;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.CLOSURES:
          loadResourceFunction = services.loadClosures;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.KUBERNETES:
          loadResourceFunction = services.loadKubernetesEntities;
          break;

        case constants.RESOURCES.SEARCH_CATEGORY.PROJECTS:
          loadResourceFunction = services.loadProjects;
          break;

        default:
        case constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS:
          loadResourceFunction = services.loadContainers;
          break;
      }
      operation.forPromise(loadResourceFunction(queryOptions)).then((result) => {
        return this.decorateContainers(result, queryOptions.$category, false);

      }).catch((e) => {
        console.log('Cannot load ' + queryOptions.$category, e);

        this.setInData(['listView', 'itemsLoading'], false);
        this.emitChange();
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
        }).catch((e) => {
          console.log('Cannot load containers next page', e);
          this.setInData(['listView', 'itemsLoading'], false);
          this.emitChange();
        });
    }

    this.emitChange();
  },

  onRescanContainers: function(queryOptions) {
    if (queryOptions && queryOptions.$category !== constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS) {
      return;
    }

    let alreadyLoadedItems = this.selectFromData(['listView', 'items']).get();
    let numberOfContainers = alreadyLoadedItems && alreadyLoadedItems.length;

    var operation =
      this.requestCancellableOperation(constants.CONTAINERS.OPERATION.LIST, queryOptions);

    if (operation) {
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.rescanContainers(queryOptions, numberOfContainers))
        .then((result) => {
          let containers = result.documentLinks.map((documentLink) => {
            return result.documents[documentLink];
          });

          let updatedItems = this.data.listView ? this.data.listView.items : [];
          alreadyLoadedItems.forEach((item) => {
            let container = containers.find((updatedContainer) => {
              return updatedContainer.documentSelfLink === item.documentSelfLink;
            });

            let updatedItem = updateContainerItem(item, container);
            if (updatedItem) {
              updatedItems = utils.updateItems(updatedItems, updatedItem,
                'documentSelfLink', updatedItem.documentSelfLink);
              this.setInData(['listView', 'items'], updatedItems);
            }
          });

          this.setInData(['listView', 'itemsLoading'], false);
          this.emitChange();
        }).catch((e) => {
          console.log('Containers rescan failed', e);

          this.setInData(['listView', 'itemsLoading'], false);
          this.emitChange();
      });
    }
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

  onOpenManageVolumes: function(volumeId) {
    var operation = this.requestCancellableOperation(constants.RESOURCES.VOLUMES.OPERATION.MANAGE);

    if (operation) {
      operation.forPromise(services.manageVolume(volumeId))
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

  onRescanContainer: function() {
    let cursor = getSelectedContainerDetailsCursor.call(this);
    let selectedContainerDetails = cursor && cursor.get();

    if (!selectedContainerDetails || !selectedContainerDetails.documentId) {
      return;
    }

    this.setInData(['rescanningContainer'], true);
    this.emitChange();

    let item = selectedContainerDetails.instance;
    services.loadContainer(selectedContainerDetails.documentId).then((container) => {
      let updatedItem = updateContainerItem(item, container);

      if (updatedItem) {
        updateSelectedContainerDetails.call(this, ['instance'], updatedItem);
      }

      this.emitChange();

    }).catch((e) => {
      console.log('Container rescan failed', e);
      this.setInData(['rescanningContainer'], false);
      this.emitChange();
    });
  },

  onStopRescanContainer: function() {
    this.setInData(['rescanningContainer'], false);
    this.emitChange();
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

  onOpenCreateOrEditProject: function(project) {
    project = project || {};
    this.setInData(['creatingResource'], project);
    this.setInData(['listView', 'queryOptions', '$category'],
                   constants.RESOURCES.SEARCH_CATEGORY.PROJECTS);
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

   onOpenCreateKubernetesEntities: function() {
    this.setInData(['creatingResource'], {});
    this.setInData(['listView', 'queryOptions', '$category'],
                      constants.RESOURCES.SEARCH_CATEGORY.KUBERNETES);
    this.emitChange();
  },

  onCreateContainer: function(containerDescription) {
    services.createContainer(containerDescription).then((request) => {
      this.navigateContainersListViewAndOpenRequests(request);
    }).catch(this.onGenericCreateError);
  },

  onCreateNetwork: function(networkDescription, hostIds) {
    // clear error
    this.setInData(['creatingResource', 'error', '_generic'], null);
    this.emitChange();

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

  onCreateKubernetesEntities: function(entitiesContent) {
    services.createKubernetesEntities(entitiesContent).then((request) => {
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
    let selectedItemDetailsCursor = getSelectedContainerDetailsCursor.call(this);
    var selectedContainerDetails = selectedItemDetailsCursor && selectedItemDetailsCursor.get();

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
    let selectedItemDetailsCursor = getSelectedContainerDetailsCursor.call(this);
    var selectedContainerDetails = selectedItemDetailsCursor && selectedItemDetailsCursor.get();

    if (!selectedContainerDetails || !selectedContainerDetails.documentId) {
      return;
    }

    var operation = this.requestCancellableOperation(constants.CONTAINERS.OPERATION.LOGS);
    if (operation) {
      operation.forPromise(services.loadContainerLogs(selectedContainerDetails.documentId,
          selectedContainerDetails.logsSettings))
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

  onChangeLogsTailLines: function(lines) {
    updateSelectedContainerDetails.call(this, ['logsSettings', 'tailLines'], lines);
    localStorage.logsTailLines = lines;
    this.emitChange();
  },

  onChangeLogsFormat: function(format) {
    updateSelectedContainerDetails.call(this, ['logsSettings', 'format'], format);
    localStorage.logsFormat = format;
    this.emitChange();
  },

  onChangeLogsOption: function(option) {
    updateSelectedContainerDetails.call(this, ['logsSettings', 'option'], option);
    localStorage.logsOption = option;
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
    if (utils.getIn(this.data, ['creatingResource'])) {
      // on another screen, do nothing
      return;
    }

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

  onProjectOperationCompleted: function(operationType) {
    if (operationType === constants.RESOURCES.PROJECTS.OPERATION.CREATE) {
      this.navigateToContainersListView(true);
    } else if (operationType === constants.RESOURCES.PROJECTS.OPERATION.EDIT
            || operationType === constants.RESOURCES.PROJECTS.OPERATION.REMOVE) {
      this.navigateToContainersListView(false);
    }
  },

  onProjectOperationFailed: function(operationType, error, documentId) {
    if (operationType === constants.RESOURCES.PROJECTS.OPERATION.CREATE
        || operationType === constants.RESOURCES.PROJECTS.OPERATION.EDIT) {
      this.onGenericCreateError(error);
    } else if (operationType === constants.RESOURCES.PROJECTS.OPERATION.REMOVE) {
      if (documentId) {
        this.setItemError('projectErrors', documentId, error);
      } else {
        this.navigateToContainersListView(false);
      }
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
      compositeComponentDetails.listView.volumes = currentCompositeComponent.listView.volumes;
      compositeComponentDetails.listView.volumeLinks =
                                                  currentCompositeComponent.listView.volumeLinks;
    }

    parentCursor.setIn(['selectedItem'], compositeComponent);
    parentCursor.setIn(['selectedItemDetails'], compositeComponentDetails);
    // clear errors
    parentCursor.setIn(['selectedItemDetails', 'error'], null);

    this.emitChange();

    operation.forPromise(Promise.all([
      services.loadCompositeComponent(compositeComponentId),
      services.loadContainersForCompositeComponent(compositeComponentId),
      services.loadNetworksForCompositeComponent(compositeComponentId),
      services.loadVolumesForCompositeComponent(compositeComponentId)
    ])).then(([retrievedCompositeComponent, childContainersResult, childNetworksResult,
      childVolumesResult]) => {

      var childContainers = utils.resultToArray(childContainersResult.documents ?
          childContainersResult.documents : childContainersResult);

      var childNetworks = utils.resultToArray(childNetworksResult.documents ?
          childNetworksResult.documents : childNetworksResult);

      var childVolumes = utils.resultToArray(childVolumesResult.documents ?
        childVolumesResult.documents : childVolumesResult);

      enhanceCompositeComponent(retrievedCompositeComponent);
      retrievedCompositeComponent.icons = getContainersImageIcons(childContainers);
      parentCursor.select(['selectedItem']).merge(retrievedCompositeComponent);
      parentCursor.select(['selectedItemDetails']).merge(retrievedCompositeComponent);

      childContainers.forEach((childContainer) => {
        enhanceContainer(childContainer);
      });

      var items = this.aggregateClusterNodes(childContainers);

      var networkLinks = getNetworkLinks(items, childNetworks);

      var volumeLinks = getVolumeLinks(items, childVolumes);

      parentCursor.select(['selectedItemDetails', 'listView'])
        .setIn(['items'], items)
        .setIn(['itemsLoading'], false)
        .setIn(['networks'], childNetworks)
        .setIn(['networkLinks'], networkLinks)
        .setIn(['volumes'], childVolumes)
        .setIn(['volumeLinks'], volumeLinks);

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

  onRescanApplicationContainers: function(compositeComponentId) {
    let parentCursor = this.selectComponent(null, null, null);

    let currentCompositeComponent = parentCursor.select(['selectedItemDetails']).get();
    if (!currentCompositeComponent
            || currentCompositeComponent.documentId !== compositeComponentId) {
      return;
    }

    this.setInData(['rescanningApplicationContainers'], true);
    this.emitChange();

    let items = currentCompositeComponent.listView && currentCompositeComponent.listView.items;

    services.loadContainersForCompositeComponent(compositeComponentId)
                .then((childContainersResult) => {
      let containers = utils.resultToArray(childContainersResult.documents ?
                                      childContainersResult.documents : childContainersResult);

      let updatedItems = currentCompositeComponent.listView
                            ? currentCompositeComponent.listView.items : [];
      items.forEach((item) => {
        let container = containers.find((updatedContainer) => {
          return updatedContainer.documentSelfLink === item.documentSelfLink;
        });

        let updatedItem = updateContainerItem(item, container);
        if (updatedItem) {
          updatedItems = utils.updateItems(updatedItems, updatedItem,
                                            'documentSelfLink', updatedItem.documentSelfLink);
          parentCursor.select(['selectedItemDetails']).setIn(['listView', 'items'], updatedItems);
        }
      });

      this.emitChange();
    }).catch((e) => {
      console.log('Application containers rescan failed', e);
      this.setInData(['rescanningApplicationContainers'], false);
      this.emitChange();
    });

    this.emitChange();
  },

  onStopRescanApplicationContainers: function() {
    this.setInData(['rescanningApplicationContainers'], false);
    this.emitChange();
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
        tailLines: localStorage.logsTailLines
          || constants.CONTAINERS.LOGS.TAIL_LINES[0],
        format: localStorage.logsFormat || constants.CONTAINERS.LOGS.FORMAT.ANSI,
        option: localStorage.logsOption || constants.CONTAINERS.LOGS.OPTION.TAIL
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
        }, (error) => {
            if (error.status === constants.ERRORS.NOT_FOUND) {
              if (currentItemDetailsCursor.getIn(['documentId']) === containerId) {
                currentItemDetailsCursor.setIn(['descriptionLinkToConvertToTemplate'],
                  container.descriptionLink);

                this.emitChange();
              }
            } else {
              console.warn('Error when loading template container description! Error: ', error);
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
      return services.copyContainerTemplate(template, true);
    }).then((copyTemplate) => {
      if (cursor.getIn(['documentId']) !== container.documentId) {
        return;
      }

      cursor.setIn(['operationInProgress'], null);
      this.emitChange();

      var templateId = utils.getDocumentId(copyTemplate.documentSelfLink);

      actions.NavigationActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE,
          templateId);
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
    }).catch(this.onGenericDetailsError);
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
  setItemError(errorType, itemDocumentId, error) {
    if (errorType && itemDocumentId) {
      this.setInData(['listView', errorType, itemDocumentId], error);
      this.emitChange();
    }
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
