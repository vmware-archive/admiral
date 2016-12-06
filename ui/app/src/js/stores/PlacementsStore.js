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
import utils from 'core/utils';
import PlacementZonesStore from 'stores/PlacementZonesStore';
import ResourceGroupsStore from 'stores/ResourceGroupsStore';
import DeploymentPolicyStore from 'stores/DeploymentPolicyStore';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let _enhancePlacement = function(placement) {
  let placementEditData = this.selectFromData(['placements', 'editingItemData']).get();

  let placementZones = (placementEditData && placementEditData.placementZones)
                          || this.selectFromData(['placements', 'placementZones']).get();
  for (let i = 0; i < placementZones.length; i++) {
    if (placementZones[i].documentSelfLink === placement.resourcePoolLink) {
      placement.placementZoneName = placementZones[i].resourcePoolState.name;
      break;
    }
  }

  let deploymentPolicies = (placementEditData && placementEditData.deploymentPolicies)
                              || this.selectFromData(['placements', 'deploymentPolicies']).get();
  for (let i = 0; i < deploymentPolicies.length; i++) {
    if (deploymentPolicies[i].documentSelfLink === placement.deploymentPolicyLink) {
      placement.deploymentPolicyName = deploymentPolicies[i].name;
      break;
    }
  }

  placement.numOfInstances = placement.allocatedInstancesCount;
  // maxNumberInstances = 0 means Unlimited
  placement.instancesPercentage = placement.maxNumberInstances > 0
    ? Math.round((placement.allocatedInstancesCount / placement.maxNumberInstances) * 100 * 10) / 10
    : 0;

  let groupId = utils.getGroup(placement.tenantLinks);
  if (groupId) {
    let groups = (placementEditData && placementEditData.groups)
                  || this.selectFromData(['placements', 'groups']).get();

    placement.groupId = groupId;
    placement.group = groups && _getGroup(groupId, groups);

    placement.groupName = groupId;
    if (placement.group) {
      placement.groupName = placement.group.label ? placement.group.label : placement.group.name;
    }
  }
};

let _getGroup = function(id, groups) {
  let matchingGroups = groups.filter((group) => {
    return group.id === id || group.documentSelfLink === id;
  });

  return matchingGroups.length > 0 ? matchingGroups[0] : null;
};

let onPlacementCreated = function(placement) {
  _enhancePlacement.call(this, placement);

  var immutablePlacement = Immutable(placement);

  var placements = this.data.placements.items.asMutable();
  placements.push(immutablePlacement);

  this.setInData(['placements', 'items'], placements);
  this.setInData(['placements', 'newItem'], immutablePlacement);
  this.setInData(['placements', 'editingItemData'], null);
  this.emitChange();

  // After we notify listeners, the new item is no longer actual
  this.setInData(['placements', 'newItem'], null);
};

let onPlacementUpdated = function(placement) {
  _enhancePlacement.call(this, placement);

  var immutablePlacement = Immutable(placement);

  var placements = this.data.placements.items.asMutable();

  for (var i = 0; i < placements.length; i++) {
    if (placements[i].documentSelfLink === immutablePlacement.documentSelfLink) {
      placements[i] = immutablePlacement;
    }
  }

  this.setInData(['placements', 'items'], placements);
  this.setInData(['placements', 'updatedItem'], immutablePlacement);
  this.setInData(['placements', 'editingItemData'], null);
  this.emitChange();

  // After we notify listeners, the new item is no longer actual
  this.setInData(['placements', 'updatedItem'], null);
};

let _createDto = function(placement) {
  var dto = $.extend({}, placement);
  dto.resourcePoolLink = dto.placementZone ? dto.placementZone.documentSelfLink : null;
  dto.deploymentPolicyLink = dto.deploymentPolicy ? dto.deploymentPolicy.documentSelfLink : null;

  dto.name = placement.name ? placement.name
                : (dto.group || 'group') + ' : ' + (dto.resourcePoolLink || 'resourcePoolLink');
  dto.tenantLinks = [];
  if (dto.groupId) {
    let tenantLink = dto.groupId;

    if (utils.isApplicationEmbedded() && tenantLink.indexOf('/tenants/') === 0
          && tenantLink.indexOf('/groups/') > -1) {
      dto.tenantLinks.push(tenantLink.substring(0, tenantLink.indexOf('/groups/')));
    }

    let groups = this.selectFromData(['placements', 'groups']).get();
    if (groups) {
      let group = _getGroup(dto.groupId, groups);
      if (group && group.documentSelfLink) {
        tenantLink = group.documentSelfLink;
      }
    }
    dto.tenantLinks.push(tenantLink);
  }

  delete dto.deploymentPolicy;
  delete dto.placementZone;
  delete dto.groupId;

  return dto;
};

let PlacementsStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {

    PlacementZonesStore.listen((placementZonesData) => {

      if (this.isContextPanelActive(constants.CONTEXT_PANEL.RESOURCE_POOLS)) {
        this.setActiveItemData(placementZonesData);

        var itemToSelect = placementZonesData.newItem || placementZonesData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);

          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['placements', 'editingItemData', 'selectedPlacementZone'],
                itemToSelect);
            this.emitChange();

            this.closeToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }

        this.emitChange();
      }

      if (placementZonesData.items && this.data.placements &&
          this.data.placements.editingItemData) {

        this.setInData(['placements', 'editingItemData', 'placementZones'],
            placementZonesData.items);
        this.emitChange();
      }
    });

    if (!utils.isApplicationEmbedded()) {
      ResourceGroupsStore.listen((resourceGroupsData) => {

        if (this.isContextPanelActive(constants.CONTEXT_PANEL.RESOURCE_GROUPS)) {
          this.setActiveItemData(resourceGroupsData);

          var itemToSelect = resourceGroupsData.newItem || resourceGroupsData.updatedItem;
          if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
            clearTimeout(this.itemSelectTimeout);

            this.itemSelectTimeout = setTimeout(() => {
              this.setInData(['placements', 'editingItemData', 'selectedGroup'], itemToSelect);
              this.emitChange();

              this.onCloseToolbar();
            }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
          }

          this.emitChange();
        }

        if (resourceGroupsData.items && this.data.placements
            && this.data.placements.editingItemData) {

          this.setInData(['placements', 'editingItemData', 'groups'], resourceGroupsData.items);
          this.emitChange();
        }

      });
    }

    DeploymentPolicyStore.listen((placementsData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES)) {
        this.setActiveItemData(placementsData);

        var itemToSelect = placementsData.newItem || placementsData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['placements', 'editingItemData', 'selectedDeploymentPolicy'],
              itemToSelect);
            this.emitChange();

            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }

        this.emitChange();
      }

      if (placementsData.items && this.data.placements && this.data.placements.editingItemData) {
        this.setInData(['placements', 'editingItemData', 'deploymentPolicies'],
          placementsData.items);
        this.emitChange();
      }
    });
  },

  listenables: [
    actions.PlacementActions,
    actions.PlacementContextToolbarActions
  ],

  onOpenPlacements: function() {
    this.setInData(['placements', 'editingItemData'], null);
    this.setInData(['contextView'], {});
    this.setInData(['error'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['placements', 'items'], constants.LOADING);

      if (utils.isApplicationEmbedded()) {
        this.setInData(['placements', 'groups'], constants.LOADING);

        operation.forPromise(Promise.all([
          services.loadPlacements(),
          services.loadGroups()
        ])).then(([placementsResult, groupsResult]) => {

          this.setInData(['placements', 'groups'], Object.values(groupsResult));
          this.emitChange();

          this.processPlacements(placementsResult);
        });
      } else {
        operation.forPromise(
          services.loadPlacements()
        ).then((placementsResult) => {

          this.processPlacements(placementsResult);
        });
      }
    }

    this.emitChange();

    actions.PlacementZonesActions.retrievePlacementZones();
    if (!utils.isApplicationEmbedded()) {
      actions.ResourceGroupsActions.retrieveGroups();
    }
    actions.DeploymentPolicyActions.retrieveDeploymentPolicies();
  },

  processPlacements: function(placementsResult) {
    var placements = utils.resultToArray(placementsResult);

    let resourcePoolLinks = placements
        .filter((placement) => placement.resourcePoolLink)
        .map((placement) => placement.resourcePoolLink);

    let deploymentPolicyLinks = placements
        .filter((placement) => placement.deploymentPolicyLink)
        .map((placement) => placement.deploymentPolicyLink);

    var calls = [
      services.loadPlacementZones([...new Set(resourcePoolLinks)]).then((result) => {
        this.setInData(['placements', 'placementZones'], Object.values(result));
      }),
      services.loadDeploymentPolicies([...new Set(deploymentPolicyLinks)]).then((result) => {
        this.setInData(['placements', 'deploymentPolicies'], Object.values(result));
      })
    ];
    if (!utils.isApplicationEmbedded()) {
      let resourceGroupLinks = placements
                                    .filter((placement) => placement.tenantLinks)
                                    .map((placement) => utils.getGroup(placement.tenantLinks));

      calls.push(services.loadResourceGroups([...new Set(resourceGroupLinks)]).then((result) => {
        this.setInData(['placements', 'groups'], Object.values(result));
      }));
    }
    Promise.all(calls).then(() => {
      placements.forEach((placement) => {
        _enhancePlacement.call(this, placement);
      });

      this.setInData(['placements', 'items'], placements);
      this.emitChange();
    });
  },

  onEditPlacement: function(placement) {
    this.clearEditError();

    var placementModel = {};
    if (placement) {
      placementModel = $.extend({}, placement);
    }

    var placementZones = PlacementZonesStore.getData().items;

    if (placementZones && placement) {
      for (let i = 0; i < placementZones.length; i++) {
        var placementZone = placementZones[i];
        if (placementZone.documentSelfLink === placement.resourcePoolLink) {
          placementModel.placementZone = placementZone.resourcePoolState;
          break;
        }
      }
    }

    var deploymentPolicies = DeploymentPolicyStore.getData().items;

    if (deploymentPolicies && placement) {
      for (let i = 0; i < deploymentPolicies.length; i++) {
        var deploymentPolicy = deploymentPolicies[i];
        if (deploymentPolicy.documentSelfLink === placement.deploymentPolicyLink) {
          placementModel.deploymentPolicy = deploymentPolicy;
          break;
        }
      }
    }

    let groups = utils.isApplicationEmbedded()
                    ? this.selectFromData(['placements', 'groups']).get()
                    : ResourceGroupsStore.getData().items;
    if (groups && placement) {
      let groupId = utils.getGroup(placement.tenantLinks);
      placementModel.groupId = groupId;

      if (groupId) {
        placementModel.group = _getGroup(groupId, groups);
      }
    }

    this.setInData(['placements', 'editingItemData', 'item'], placementModel);
    this.setInData(['placements', 'editingItemData', 'placementZones'], placementZones);
    this.setInData(['placements', 'editingItemData', 'deploymentPolicies'], deploymentPolicies);
    this.setInData(['placements', 'editingItemData', 'groups'], groups);

    this.emitChange();
  },

  onCancelEditPlacement: function() {
    this.setInData(['placements', 'editingItemData'], null);
    this.emitChange();
  },

  onCreatePlacement: function(placement) {
    this.clearEditError();

    this.setInData(['placements', 'editingItemData', 'selectedGroup'], placement.groupId);

    var placementDto = _createDto.call(this, placement);

    services.createPlacement(placementDto).then((placement) => {

      onPlacementCreated.call(this, placement);

    }).catch(this.onGenericEditError);
  },

  onUpdatePlacement: function(placement) {
    this.clearEditError();

    this.setInData(['placements', 'editingItemData', 'selectedGroup'], placement.groupId);

    var dto = _createDto.call(this, placement);

    services.updatePlacement(dto).then((placement) => {
      // If the backend did not make any changes, the response will be empty
      placement = placement || dto;

      onPlacementUpdated.call(this, placement);

    }).catch(this.onGenericEditError);
  },

  onDeletePlacement: function(placement) {
    this.clearGeneralError();

    services.deletePlacement(placement).then(() => {
      var placements = this.data.placements.items.asMutable();

      for (var i = placements.length - 1; i >= 0; i--) {
        if (placements[i].documentSelfLink === placement.documentSelfLink) {
          placements.splice(i, 1);
        }
      }

      this.setInData(['placements', 'items'], placements);
      this.emitChange();

    }).catch(this.onPlacementDeleteError);
  },

  onOpenToolbarPlacementZones: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      false);
  },

  onOpenToolbarResourceGroups: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_GROUPS, ResourceGroupsStore.getData(),
      false);
  },

  onOpenToolbarDeploymentPolicies: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), false);
  },

  onCloseToolbar: function() {
    this.closeToolbar();
  },

  onCreatePlacementZone: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      true);
    actions.PlacementZonesActions.editPlacementZone();
  },

  onManagePlacementZones: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      true);
  },

  onCreateResourceGroup: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_GROUPS, ResourceGroupsStore.getData(),
      true);
    actions.ResourceGroupsActions.editGroup();
  },

  onManageResourceGroups: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_GROUPS, ResourceGroupsStore.getData(),
      true);
  },

  onCreateDeploymentPolicy: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), true);
    actions.DeploymentPolicyActions.editDeploymentPolicy();
  },

  onManageDeploymentPolicies: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES,
      DeploymentPolicyStore.getData(), true);
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);

    this.setInData(['placements', 'editingItemData', 'validationErrors'], validationErrors);
    this.emitChange();
  },

  onPlacementDeleteError: function(e) {
    this.setInData(['error'], utils.getErrorMessage(e));
    this.emitChange();
  },

  clearEditError: function() {
    this.setInData(['placements', 'editingItemData', 'validationErrors'], null);
    this.emitChange();
  },

  clearGeneralError: function() {
    this.setInData(['error'], null);
    this.emitChange();
  }
});

export default PlacementsStore;

