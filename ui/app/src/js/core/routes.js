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
import modal from 'core/modal';
import constants from 'core/constants';
import computeConstants from 'core/computeConstants';
import utils from 'core/utils';
import docs from 'core/docs';

var _isFirstTimeUser = true;

crossroads.addRoute('/', function() {
  if (_isFirstTimeUser) {
    hasher.setHash('home');
  } else {
    hasher.setHash('hosts');
  }
});

crossroads.addRoute('/home', function() {
  actions.AppActions.openHome();
});

crossroads.addRoute('/home/newHost', function() {
  actions.AppActions.openHome();
  actions.HostActions.openAddHost();
});

crossroads.addRoute('/hosts:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.HOSTS.name);
  actions.HostActions.openHosts(query);
});

crossroads.addRoute('/hosts/new', function() {
  actions.AppActions.openView(constants.VIEWS.HOSTS.name);
  actions.HostActions.openAddHost();
});

crossroads.addRoute('/hosts/{hostId*}', function(hostId) {
  actions.AppActions.openView(constants.VIEWS.HOSTS.name);
  actions.HostActions.editHost(hostId);
});

crossroads.addRoute('/placements', function() {
  actions.AppActions.openView(constants.VIEWS.PLACEMENTS.name);
  actions.PlacementActions.openPlacements();
});

crossroads.addRoute('/templates:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openTemplates(query, true);
});

crossroads.addRoute('/templates/image/{imageId*}/newContainer', function(imageId) {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openTemplates();
  actions.TemplateActions.openContainerRequest(constants.TEMPLATES.TYPES.IMAGE, imageId);
});

crossroads.addRoute('/templates/template/{templateId*}', function(templateId) {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openTemplates();
  actions.TemplateActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE, templateId);
});

crossroads.addRoute('/templates/template/{templateId*}/newContainer', function(templateId) {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openTemplates();
  actions.TemplateActions.openContainerRequest(constants.TEMPLATES.TYPES.TEMPLATE, templateId);
});

crossroads.addRoute('/registries', function() {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.RegistryActions.openRegistries();
});

crossroads.addRoute('/import-template', function() {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openImportTemplate();
});

crossroads.addRoute('/containers:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/containers/new-container', function() {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCreateContainer();
});

crossroads.addRoute('/containers/new-network', function() {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCreateNetwork();
});

crossroads.addRoute('containers/composite/{compositeComponentId*}' +
                    '/cluster/{clusterId*}/containers/{childContainerId*}',
                    function(compositeComponentId, clusterId, childContainerId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(childContainerId, clusterId, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}/containers/{childContainerId*}',
                    function(compositeComponentId, childContainerId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(childContainerId, null, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}/cluster/{clusterId*}',
                    function(compositeComponentId, clusterId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openClusterDetails(clusterId, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}', function(compositeComponentId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCompositeContainerDetails(compositeComponentId);
});

crossroads.addRoute('/containers/cluster/{clusterId*}/containers/{containerId*}',
  function(clusterId, containerId) {
    actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
    actions.ContainerActions.openContainers();
    actions.ContainerActions.openContainerDetails(containerId, clusterId);
  });


crossroads.addRoute('/containers/cluster/{clusterId*}', function(clusterId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openClusterDetails(clusterId);
});

crossroads.addRoute('/containers/{containerId*}', function(containerId) {
  actions.AppActions.openView(constants.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(containerId);
});

crossroads.addRoute('/resource-pools', function() {
  actions.AppActions.openView(computeConstants.VIEWS.RESOURCE_POOLS.name);
});

crossroads.addRoute('/environments', function() {
  actions.AppActions.openView(computeConstants.VIEWS.ENVIRONMENTS.name);
  actions.EnvironmentsActions.openEnvironments();
});

crossroads.addRoute('/machines:?query:', function(query) {
  actions.AppActions.openView(computeConstants.VIEWS.MACHINES.name);
  actions.MachineActions.openMachines(query, true);
});


crossroads.addRoute('/machines/{machineId*}', function() {
  // not yet supported
  // actions.AppActions.openView(computeConstants.VIEWS.MACHINES.name);
  // actions.MachineActions.openMachines();
  // actions.MachineActions.openMachineDetails(machineId);
});

// Nothing from the above is matched, redirect to main
crossroads.bypassed.add(function() {
  hasher.setHash('');
});

actions.NavigationActions.openHome.listen(function() {
  hasher.setHash('home');
});

actions.NavigationActions.openHomeAddHost.listen(function() {
  hasher.setHash('home/newHost');
});

actions.NavigationActions.openHosts.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('hosts', queryOptions));
});

actions.NavigationActions.openHostsSilently.listen(function() {
  hasher.changed.active = false;
  hasher.setHash('hosts');
  hasher.changed.active = true;
});

actions.NavigationActions.openAddHost.listen(function() {
  hasher.setHash('hosts/new');
});

actions.NavigationActions.editHost.listen(function(hostId) {
  hasher.setHash('hosts/' + hostId);
});

actions.NavigationActions.openTemplates.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('templates', queryOptions));
});

actions.NavigationActions.openRegistries.listen(function() {
  hasher.setHash('registries');
});

actions.NavigationActions.openTemplateDetails.listen(function(type, itemId) {
  if (type === constants.TEMPLATES.TYPES.TEMPLATE) {
    hasher.setHash('templates/template/' + itemId);
  } else {
    hasher.setHash('templates/image/' + itemId);
  }
});

actions.NavigationActions.openContainerRequest.listen(function(type, itemId) {
  if (type === constants.TEMPLATES.TYPES.TEMPLATE) {
    hasher.setHash('templates/template/' + itemId + '/newContainer');
  } else {
    hasher.setHash('templates/image/' + itemId + '/newContainer');
  }
});

actions.NavigationActions.openContainers.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('containers', queryOptions));
});

actions.NavigationActions.openContainerDetails.listen(function(containerId, clusterId,
                                                                compositeComponentId) {
  if (clusterId && compositeComponentId) {
    hasher.setHash('containers/composite/' + compositeComponentId + '/cluster/' + clusterId +
                  '/containers/' + containerId);
  } else if (clusterId) {
    hasher.setHash('containers/cluster/' + clusterId + '/containers/' + containerId);
  } else if (compositeComponentId) {
    hasher.setHash('containers/composite/' + compositeComponentId + '/containers/' + containerId);
  } else {
    hasher.setHash('containers/' + containerId);
  }
});

actions.NavigationActions.openClusterDetails.listen(function(clusterId, compositeComponentId) {
  if (compositeComponentId) {
    hasher.setHash('containers/composite/' + compositeComponentId + '/cluster/' + clusterId);
  } else {
    hasher.setHash('containers/cluster/' + clusterId);
  }
});

actions.NavigationActions.openCompositeContainerDetails.listen(function(compositeComponentId) {
  hasher.setHash('containers/composite/' + compositeComponentId);
});

actions.NavigationActions.showContainersPerPlacement.listen(function(placementId) {
  let queryOptions = {
    'placement': placementId
  };

  hasher.setHash(getHashWithQuery('containers', queryOptions));
});


actions.NavigationActions.openPlacements.listen(function() {
  hasher.setHash('placements');
});

actions.NavigationActions.openEnvironments.listen(function() {
  hasher.setHash('environments');
});

actions.NavigationActions.openMachines.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('machines', queryOptions));
});

actions.NavigationActions.openMachineDetails.listen(function() {
  // not yet supported
  // hasher.setHash('machines/' + machineId);
});

function parseHash(newHash) {
  // In case of any opened modals, through interaction with browser, we should close in any case
  modal.hide();
  crossroads.parse(newHash);

  if (newHash) {
    docs.update('/' + newHash);
  } else {
    docs.update('/');
  }
}

function getHashWithQuery(hash, queryOptions) {
  var queryString;
  if (queryOptions) {
    queryString = utils.paramsToURI(queryOptions);
  }

  if (queryString) {
    return hash + '?' + queryString;
  } else {
    return hash;
  }
}

var routes = {
  initialize: function(isFirstTimeUser) {
    _isFirstTimeUser = isFirstTimeUser;
    hasher.stop();
    hasher.initialized.add(parseHash); // Parse initial hash
    hasher.changed.add(parseHash); // Parse hash changes
    hasher.init(); // Start listening for history change
  },

  getHash: function() {
    return hasher.getHash();
  },

  getContainersHash: function(queryOptions) {
    return getHashWithQuery('containers', queryOptions);
  }
};

export default routes;
