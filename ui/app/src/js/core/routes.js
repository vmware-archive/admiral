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
import modal from 'core/modal';
import constants from 'core/constants';
import computeConstants from 'core/computeConstants';
import utils from 'core/utils';
import docs from 'core/docs';

var _isFirstTimeUser = true;

var silenced = false;

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

crossroads.addRoute('/closures/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);
  actions.ContainerActions.openContainers({
    '$category': 'closures'
  }, true);
  actions.ContainerActions.openCreateClosure();
});

crossroads.addRoute('/home/newHost', function() {
  actions.AppActions.openHome();
  actions.HostActions.openAddHost();
});

crossroads.addRoute('/hosts:?query:', function(query) {
  if (silenced) {
    return;
  }
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

crossroads.addRoute('/placementZones', function() {
  actions.AppActions.openView(computeConstants.VIEWS.PLACEMENT_ZONES.name);
  actions.PlacementZonesActions.retrievePlacementZones();
});

crossroads.addRoute('/projects:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.PROJECTS.name);

  query = query || {};
  query.$category = 'projects';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/applications:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.APPLICATIONS.name);

  query = query || {};
  query.$category = 'applications';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/networks:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.NETWORKS.name);

  query = query || {};
  query.$category = 'networks';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/volumes:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.VOLUMES.name);

  query = query || {};
  query.$category = 'volumes';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/kubernetes:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.KUBERNETES.name);

  query = query || {};
  query.$category = 'kubernetes';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/closures', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);
  actions.ContainerActions.openContainers({
    '$category': 'closures'
  }, true, true);
});

crossroads.addRoute('/closures:?query:', function(query) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);

  query = query || {};
  query.$category = 'closures';
  actions.ContainerActions.openContainers(query, true, true);
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
  actions.AppActions.openView(constants.VIEWS.REGISTRIES.name);
  actions.RegistryActions.openRegistries();
});

crossroads.addRoute('/credentials', function() {
  actions.AppActions.openView(constants.VIEWS.CREDENTIALS.name);
  actions.CredentialsActions.retrieveCredentials();
});

crossroads.addRoute('/certificates', function() {
  actions.AppActions.openView(constants.VIEWS.CERTIFICATES.name);
  actions.CertificatesActions.retrieveCertificates();
});

crossroads.addRoute('/import-template', function() {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openImportTemplate();
});

crossroads.addRoute('/containers:?query:', function(query) {
  let viewName = constants.VIEWS.RESOURCES.VIEWS.CONTAINERS.name;

  actions.AppActions.openView(viewName);

  query = query || {};
  query.$category = 'containers';
  actions.ContainerActions.openContainers(query, true);
});

crossroads.addRoute('/containers/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCreateContainer();
});

crossroads.addRoute('/applications/new', function() {
  actions.AppActions.openView(constants.VIEWS.TEMPLATES.name);
  actions.TemplateActions.openTemplates({}, true);
  actions.TemplateActions.openCreateNewTemplate();
});

crossroads.addRoute('/projects/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.PROJECTS.name);
  actions.ContainerActions.openContainers({
    '$category': 'projects'
  }, true);
  actions.ResourceGroupsActions.openCreateOrEditProject();
});

crossroads.addRoute('/networks/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.NETWORKS.name);
  actions.ContainerActions.openContainers({
    '$category': 'networks'
  }, true);
  actions.ContainerActions.openCreateNetwork();
});

crossroads.addRoute('/volumes/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.VOLUMES.name);
  actions.ContainerActions.openContainers({
    '$category': 'volumes'
  }, true);
  actions.VolumeActions.openCreateVolume();
});

crossroads.addRoute('/kubernetes/new', function() {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.KUBERNETES.name);
  actions.ContainerActions.openContainers({
    '$category': 'kubernetes'
  }, true);
  actions.KubernetesActions.openCreateKubernetesEntities();
});

crossroads.addRoute('containers/composite/{compositeComponentId*}' +
                    '/cluster/{clusterId*}/containers/{childContainerId*}',
                    function(compositeComponentId, clusterId, childContainerId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.APPLICATIONS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(childContainerId, clusterId, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}/containers/{childContainerId*}',
                    function(compositeComponentId, childContainerId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.APPLICATIONS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(childContainerId, null, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}/cluster/{clusterId*}',
                    function(compositeComponentId, clusterId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.APPLICATIONS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openClusterDetails(clusterId, compositeComponentId);
});

crossroads.addRoute('containers/composite/{compositeComponentId*}', function(compositeComponentId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.APPLICATIONS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCompositeContainerDetails(compositeComponentId);
});

crossroads.addRoute('closures/{closureDescriptionId*}', function(closureDescriptionId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openCompositeClosureDetails(closureDescriptionId);
});

crossroads.addRoute('/containers/{containerId*}', function(containerId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CONTAINERS.name);
  actions.ContainerActions.openContainers();
  actions.ContainerActions.openContainerDetails(containerId);
});

crossroads.addRoute('/closure/{closureId*}', function(closureId) {
  actions.AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);
  actions.ContainerActions.openClosureDetails(closureId);
});

crossroads.addRoute('/profiles:?query:', function(query) {
  actions.AppActions.openView(computeConstants.VIEWS.PROFILES.name);
  actions.ProfileActions.openProfiles(query);
});

crossroads.addRoute('/profiles/new', function() {
  actions.AppActions.openView(computeConstants.VIEWS.PROFILES.name);
  actions.ProfileActions.openAddProfile();
});

crossroads.addRoute('/instance-types/new', function() {
  actions.AppActions.openView(computeConstants.VIEWS.INSTANCETYPE.name);
  actions.ProfileActions.openAddInstanceType();
  actions.EndpointsActions.retrieveEndpoints();
});

crossroads.addRoute('/profiles/{id*}', function(id) {
  actions.AppActions.openView(computeConstants.VIEWS.PROFILES.name);
  actions.ProfileActions.editProfile(id);
});

crossroads.addRoute('/instance-types/edit/{id*}', function(id) {
  actions.AppActions.openView(computeConstants.VIEWS.INSTANCETYPE.name);
  actions.ProfileActions.editInstanceType(id);
  actions.EndpointsActions.retrieveEndpoints();
});

crossroads.addRoute('/endpoints', function() {
  if (silenced) {
    return;
  }
  actions.AppActions.openView(computeConstants.VIEWS.ENDPOINTS.name);
  actions.EndpointsActions.retrieveEndpoints();
});

crossroads.addRoute('/endpoints/new', function() {
  actions.AppActions.openView(computeConstants.VIEWS.ENDPOINTS.name);
  actions.EndpointsActions.editEndpoint({});
});

crossroads.addRoute('/machines:?query:', function(query) {
  actions.AppActions.openView(computeConstants.VIEWS.RESOURCES.VIEWS.MACHINES.name);
  actions.MachineActions.openMachines(query, true);
});

crossroads.addRoute('/machines/new', function() {
  actions.AppActions.openView(computeConstants.VIEWS.RESOURCES.VIEWS.MACHINES.name);
  actions.MachineActions.openAddMachine();
});

crossroads.addRoute('/machines/{machineId*}/details', function(machineId) {
  actions.AppActions.openView(computeConstants.VIEWS.RESOURCES.VIEWS.MACHINES.name);
  actions.MachineActions.openMachineDetails(machineId);
});

crossroads.addRoute('/machines/{machineId*}', function(machineId) {
  actions.AppActions.openView(computeConstants.VIEWS.RESOURCES.VIEWS.MACHINES.name);
  actions.MachineActions.editMachine(machineId);
});

crossroads.addRoute('/compute:?query:', function(query) {
  actions.AppActions.openView(computeConstants.VIEWS.COMPUTE.name);
  actions.ComputeActions.openCompute(query, true);
});

crossroads.addRoute('/compute/{computeId*}', function(computeId) {
  actions.AppActions.openView(computeConstants.VIEWS.COMPUTE.name);
  actions.ComputeActions.editCompute(computeId);
});

function addNgRoute(route, view) {
  crossroads.addRoute(route, () => {
    actions.AppActions.openView(view.name, routes.getHash());
  });
}

const K8SViews = constants.VIEWS.KUBERNETES_RESOURCES.VIEWS;
addNgRoute('/kubernetes/pods/:query*:', K8SViews.PODS);
addNgRoute('/kubernetes/services/:query*:', K8SViews.SERVICES);
addNgRoute('/kubernetes/deployments/:query*:', K8SViews.DEPLOYMENTS);
addNgRoute('/kubernetes/replication-controllers/:query*:', K8SViews.REPLICATION_CONTROLLERS);
addNgRoute('/kubernetes/applications/:query*:', K8SViews.APPLICATIONS);

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
  silenced = true;
  hasher.setHash('hosts');
  silenced = false;
});

actions.NavigationActions.openEndpointsSilently.listen(function() {
  silenced = true;
  hasher.setHash('endpoints');
  silenced = false;
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
  var category;
  if (queryOptions) {
    category = queryOptions.$category;
    queryOptions = $.extend({}, queryOptions);
    delete queryOptions.$category;
  }

  category = category || constants.CONTAINERS.SEARCH_CATEGORY.CONTAINERS;
  hasher.setHash(getHashWithQuery(category, queryOptions));
});

actions.NavigationActions.openNetworks.listen(function(queryOptions) {
  var category;
  if (queryOptions) {
    category = queryOptions.$category;
    queryOptions = $.extend({}, queryOptions);
    delete queryOptions.$category;
  }

  category = category || constants.CONTAINERS.SEARCH_CATEGORY.NETWORKS;
  hasher.setHash(getHashWithQuery(category, queryOptions));
});

actions.NavigationActions.openVolumes.listen(function(queryOptions) {
  var category;
  if (queryOptions) {
    category = queryOptions.$category;
    queryOptions = $.extend({}, queryOptions);
    delete queryOptions.$category;
  }

  category = category || constants.CONTAINERS.SEARCH_CATEGORY.VOLUMES;
  hasher.setHash(getHashWithQuery(category, queryOptions));
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

actions.NavigationActions.openClosureDetails.listen(function(closureId) {
  hasher.setHash('closure/' + closureId);
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

actions.NavigationActions.openCompositeClosureDetails.listen(function(closureDescriptionId) {
  hasher.setHash('closures/' + closureDescriptionId);
});

actions.NavigationActions.showContainersPerPlacement.listen(function(placementId) {
  let queryOptions = {
    'placement': placementId
  };

  hasher.setHash(getHashWithQuery('containers', queryOptions));
});

actions.NavigationActions.showMachinesPerPlacement.listen(function(placementId) {
  let queryOptions = {
    'placement': placementId
  };

  hasher.setHash(getHashWithQuery('machines', queryOptions));
});

actions.NavigationActions.openPlacements.listen(function() {
  hasher.setHash('placements');
});

actions.NavigationActions.openEndpoints.listen(function() {
  hasher.setHash('endpoints');
});

actions.NavigationActions.openAddEndpoint.listen(function() {
  hasher.setHash('endpoints/new');
});

actions.NavigationActions.openProfiles.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('profiles', queryOptions));
});

actions.NavigationActions.openInstanceTypes.listen(function() {
  // TODO: find a better way
  // Navigate to home so the view is hidden from the stage
  actions.AppActions.openView(null);
  actions.ProfileActions.clearProfile();
  window.top.location.href = window.origin + '/#compute/instance-types';
});

actions.NavigationActions.openAddInstanceType.listen(function() {
  hasher.setHash('instance-types/new');
});

actions.NavigationActions.editInstanceType.listen(function(id) {
  hasher.setHash('instance-types/edit/' + id);
});

actions.NavigationActions.openAddProfile.listen(function() {
  hasher.setHash('profiles/new');
});

actions.NavigationActions.editProfile.listen(function(id) {
  hasher.setHash('profiles/' + id);
});

actions.NavigationActions.openMachines.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('machines', queryOptions));
});

actions.NavigationActions.openAddMachine.listen(function() {
  hasher.setHash('machines/new');
});

actions.NavigationActions.editMachine.listen(function(machineId) {
  hasher.setHash('machines/' + machineId);
});

actions.NavigationActions.openMachineDetails.listen(function(machineId) {
  hasher.setHash('machines/' + machineId + '/details');
});

actions.NavigationActions.openCompute.listen(function(queryOptions) {
  hasher.setHash(getHashWithQuery('compute', queryOptions));
});

actions.NavigationActions.editCompute.listen(function(computeId) {
  hasher.setHash('compute/' + computeId);
});

actions.NavigationActions.openClosuresSilently.listen(function() {
  hasher.changed.active = false;
  hasher.setHash('closures');
  hasher.changed.active = true;
});

actions.NavigationActions.openClosures.listen(function() {
 hasher.setHash('closures');
});

actions.NavigationActions.openAddClosure.listen(function() {
  hasher.setHash('closures/new');
});

function parseHash(newHash) {
  // In case of any opened modals, through interaction with browser, we should close in any case
  modal.hide();
  crossroads.parse(newHash);

  if (newHash) {
    if (window.notifyNavigation) {
      window.notifyNavigation('/' + newHash);
    }
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
