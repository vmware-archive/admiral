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
import ResourcePoolsStore from 'stores/ResourcePoolsStore';
import ResourceGroupsStore from 'stores/ResourceGroupsStore';
import DeploymentPolicyStore from 'stores/DeploymentPolicyStore';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let _enhancePolicy = function(policy) {
  let policyEditData = this.selectFromData(['policies', 'editingItemData']).get();

  let resourcePools = (policyEditData && policyEditData.resourcePools)
                          || this.selectFromData(['policies', 'resourcePools']).get();
  for (let i = 0; i < resourcePools.length; i++) {
    if (resourcePools[i].documentSelfLink === policy.resourcePoolLink) {
      policy.resourcePoolName = resourcePools[i].name;
      break;
    }
  }

  let deploymentPolicies = (policyEditData && policyEditData.deploymentPolicies)
                              || this.selectFromData(['policies', 'deploymentPolicies']).get();
  for (let i = 0; i < deploymentPolicies.length; i++) {
    if (deploymentPolicies[i].documentSelfLink === policy.deploymentPolicyLink) {
      policy.deploymentPolicyName = deploymentPolicies[i].name;
      break;
    }
  }

  policy.numOfInstances = policy.allocatedInstancesCount;
  // maxNumberInstances = 0 means Unlimited
  policy.instancesPercentage = policy.maxNumberInstances > 0
    ? Math.round((policy.allocatedInstancesCount / policy.maxNumberInstances) * 100 * 10) / 10 : 0;

  let groupId = utils.getGroup(policy.tenantLinks);
  if (groupId) {
    let groups = (policyEditData && policyEditData.groups)
                  || this.selectFromData(['policies', 'groups']).get();

    policy.groupId = groupId;
    policy.group = groups && _getGroup(groupId, groups);

    policy.groupName = groupId;
    if (policy.group) {
      policy.groupName = policy.group.label ? policy.group.label : policy.group.name;
    }
  }
};

let _getGroup = function(id, groups) {
  let matchingGroups = groups.filter((group) => {
    return group.id === id || group.documentSelfLink === id;
  });

  return matchingGroups.length > 0 ? matchingGroups[0] : null;
};

let onPolicyCreated = function(policy) {
  _enhancePolicy.call(this, policy);

  var immutablePolicy = Immutable(policy);

  var policies = this.data.policies.items.asMutable();
  policies.push(immutablePolicy);

  this.setInData(['policies', 'items'], policies);
  this.setInData(['policies', 'newItem'], immutablePolicy);
  this.setInData(['policies', 'editingItemData'], null);
  this.emitChange();

  // After we notify listeners, the new item is no logner actual
  this.setInData(['policies', 'newItem'], null);
};

let onPolicyUpdated = function(policy) {
  _enhancePolicy.call(this, policy);

  var immutablePolicy = Immutable(policy);

  var policies = this.data.policies.items.asMutable();

  for (var i = 0; i < policies.length; i++) {
    if (policies[i].documentSelfLink === immutablePolicy.documentSelfLink) {
      policies[i] = immutablePolicy;
    }
  }

  this.setInData(['policies', 'items'], policies);
  this.setInData(['policies', 'updatedItem'], immutablePolicy);
  this.setInData(['policies', 'editingItemData'], null);
  this.emitChange();

  // After we notify listeners, the new item is no longer actual
  this.setInData(['policies', 'updatedItem'], null);
};

let _createDto = function(policy) {
  var dto = $.extend({}, policy);
  dto.resourcePoolLink = dto.resourcePool ? dto.resourcePool.documentSelfLink : null;
  dto.deploymentPolicyLink = dto.deploymentPolicy ? dto.deploymentPolicy.documentSelfLink : null;

  dto.name = policy.name ? policy.name
                : (dto.group || 'group') + ' : ' + (dto.resourcePoolLink || 'resourcePoolLink');
  dto.tenantLinks = [];
  if (dto.groupId) {
    let tenantLink = dto.groupId;

    if (utils.isApplicationEmbedded() && tenantLink.indexOf('/tenants/') === 0
          && tenantLink.indexOf('/groups/') > -1) {
      dto.tenantLinks.push(tenantLink.substring(0, tenantLink.indexOf('/groups/')));
    }

    let groups = this.selectFromData(['policies', 'groups']).get();
    if (groups) {
      let group = _getGroup(dto.groupId, groups);
      if (group && group.documentSelfLink) {
        tenantLink = group.documentSelfLink;
      }
    }
    dto.tenantLinks.push(tenantLink);
  }

  delete dto.deploymentPolicy;
  delete dto.resourcePool;
  delete dto.groupId;

  return dto;
};

let PoliciesStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {

    ResourcePoolsStore.listen((resourcePoolsData) => {

      if (this.isContextPanelActive(constants.CONTEXT_PANEL.RESOURCE_POOLS)) {
        this.setActiveItemData(resourcePoolsData);

        var itemToSelect = resourcePoolsData.newItem || resourcePoolsData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);

          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['policies', 'editingItemData', 'selectedResourcePool'], itemToSelect);
            this.emitChange();

            this.closeToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }

        this.emitChange();
      }

      if (resourcePoolsData.items && this.data.policies && this.data.policies.editingItemData) {

        this.setInData(['policies', 'editingItemData', 'resourcePools'], resourcePoolsData.items);
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
              this.setInData(['policies', 'editingItemData', 'selectedGroup'], itemToSelect);
              this.emitChange();

              this.onCloseToolbar();
            }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
          }

          this.emitChange();
        }

        if (resourceGroupsData.items && this.data.policies && this.data.policies.editingItemData) {

          this.setInData(['policies', 'editingItemData', 'groups'], resourceGroupsData.items);
          this.emitChange();
        }

      });
    }

    DeploymentPolicyStore.listen((policiesData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.DEPLOYMENT_POLICIES)) {
        this.setActiveItemData(policiesData);

        var itemToSelect = policiesData.newItem || policiesData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['policies', 'editingItemData', 'selectedDeploymentPolicy'],
              itemToSelect);
            this.emitChange();

            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }

        this.emitChange();
      }

      if (policiesData.items && this.data.policies && this.data.policies.editingItemData) {
        this.setInData(['policies', 'editingItemData', 'deploymentPolicies'],
          policiesData.items);
        this.emitChange();
      }
    });
  },

  listenables: [
    actions.PolicyActions,
    actions.PolicyContextToolbarActions
  ],

  onOpenPolicies: function() {
    this.setInData(['policies', 'editingItemData'], null);
    this.setInData(['contextView'], {});

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['policies', 'items'], constants.LOADING);

      if (utils.isApplicationEmbedded()) {
        this.setInData(['policies', 'groups'], constants.LOADING);

        operation.forPromise(Promise.all([
          services.loadPolicies(),
          services.loadGroups()
        ])).then(([policiesResult, groupsResult]) => {

          this.setInData(['policies', 'groups'], Object.values(groupsResult));
          this.emitChange();

          this.processPolicies(policiesResult);
        });
      } else {
        operation.forPromise(
          services.loadPolicies()
        ).then((policiesResult) => {

          this.processPolicies(policiesResult);
        });
      }
    }

    this.emitChange();

    actions.ResourcePoolsActions.retrieveResourcePools();
    if (!utils.isApplicationEmbedded()) {
      actions.ResourceGroupsActions.retrieveGroups();
    }
    actions.DeploymentPolicyActions.retrieveDeploymentPolicies();
  },

  processPolicies: function(policiesResult) {
    var policies = utils.resultToArray(policiesResult);

    let resourcePoolLinks = policies
        .filter((policy) => policy.resourcePoolLink)
        .map((policy) => policy.resourcePoolLink);

    let deploymentPolicyLinks = policies
        .filter((policy) => policy.deploymentPolicyLink)
        .map((policy) => policy.deploymentPolicyLink);

    var calls = [
      services.loadResourcePools([...new Set(resourcePoolLinks)]).then((result) => {
        this.setInData(['policies', 'resourcePools'], Object.values(result));
      }),
      services.loadDeploymentPolicies([...new Set(deploymentPolicyLinks)]).then((result) => {
        this.setInData(['policies', 'deploymentPolicies'], Object.values(result));
      })
    ];
    if (!utils.isApplicationEmbedded()) {
      let resourceGroupLinks = policies
                                    .filter((policy) => policy.tenantLinks)
                                    .map((policy) => utils.getGroup(policy.tenantLinks));

      calls.push(services.loadResourceGroups([...new Set(resourceGroupLinks)]).then((result) => {
        this.setInData(['policies', 'groups'], Object.values(result));
      }));
    }
    Promise.all(calls).then(() => {
      policies.forEach((policy) => {
        _enhancePolicy.call(this, policy);
      });

      this.setInData(['policies', 'items'], policies);
      this.emitChange();
    });
  },

  onEditPolicy: function(policy) {
    this.clearEditError();

    var policyModel = {};
    if (policy) {
      policyModel = $.extend({}, policy);
    }

    var resourcePools = ResourcePoolsStore.getData().items;

    if (resourcePools && policy) {
      for (let i = 0; i < resourcePools.length; i++) {
        var resourcePool = resourcePools[i];
        if (resourcePool.documentSelfLink === policy.resourcePoolLink) {
          policyModel.resourcePool = resourcePool;
          break;
        }
      }
    }

    var deploymentPolicies = DeploymentPolicyStore.getData().items;

    if (deploymentPolicies && policy) {
      for (let i = 0; i < deploymentPolicies.length; i++) {
        var deploymentPolicy = deploymentPolicies[i];
        if (deploymentPolicy.documentSelfLink === policy.deploymentPolicyLink) {
          policyModel.deploymentPolicy = deploymentPolicy;
          break;
        }
      }
    }

    let groups = utils.isApplicationEmbedded()
                    ? this.selectFromData(['policies', 'groups']).get()
                    : ResourceGroupsStore.getData().items;
    if (groups && policy) {
      let groupId = utils.getGroup(policy.tenantLinks);
      policyModel.groupId = groupId;

      if (groupId) {
        policyModel.group = _getGroup(groupId, groups);
      }
    }

    this.setInData(['policies', 'editingItemData', 'item'], policyModel);
    this.setInData(['policies', 'editingItemData', 'resourcePools'], resourcePools);
    this.setInData(['policies', 'editingItemData', 'deploymentPolicies'], deploymentPolicies);
    this.setInData(['policies', 'editingItemData', 'groups'], groups);

    this.emitChange();
  },

  onCancelEditPolicy: function() {
    this.setInData(['policies', 'editingItemData'], null);
    this.emitChange();
  },

  onCreatePolicy: function(policy) {
    this.clearEditError();

    var policyDto = _createDto.call(this, policy);

    services.createPolicy(policyDto).then((policy) => {

      onPolicyCreated.call(this, policy);

    }).catch(this.onGenericEditError);
  },

  onUpdatePolicy: function(policy) {
    this.clearEditError();

    var dto = _createDto.call(this, policy);

    services.updatePolicy(dto).then((policy) => {
      // If the backend did not make any changes, the response will be empty
      policy = policy || dto;

      onPolicyUpdated.call(this, policy);

    }).catch(this.onGenericEditError);
  },

  onDeletePolicy: function(policy) {
    this.clearGeneralError();

    services.deletePolicy(policy).then(() => {
      var policies = this.data.policies.items.asMutable();

      for (var i = policies.length - 1; i >= 0; i--) {
        if (policies[i].documentSelfLink === policy.documentSelfLink) {
          policies.splice(i, 1);
        }
      }

      this.setInData(['policies', 'items'], policies);
      this.emitChange();

    }).catch(this.onPolicyDeleteError);
  },

  onOpenToolbarResourcePools: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, ResourcePoolsStore.getData(),
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

  onCreateResourcePool: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, ResourcePoolsStore.getData(),
      true);
    actions.ResourcePoolsActions.editResourcePool();
  },

  onManageResourcePools: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, ResourcePoolsStore.getData(),
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

    this.setInData(['policies', 'editingItemData', 'validationErrors'], validationErrors);
    this.emitChange();
  },

  onPolicyDeleteError: function(e) {
    this.setInData(['error'], utils.getErrorMessage(e));
    this.emitChange();
  },

  clearEditError: function() {
    this.setInData(['policies', 'editingItemData', 'validationErrors'], null);
    this.emitChange();
  },

  clearGeneralError: function() {
    this.setInData(['error'], null);
    this.emitChange();
  }
});

export default PoliciesStore;

