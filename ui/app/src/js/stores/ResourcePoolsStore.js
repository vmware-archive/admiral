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
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import EndpointsStore from 'stores/EndpointsStore';

const OPERATION = {
  LIST: 'list'
};

let ResourcePoolsStore = Reflux.createStore({
  listenables: [actions.ResourcePoolsActions, actions.ResourcePoolsContextToolbarActions],
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
    EndpointsStore.listen((endpointsData) => {
      if (this.isContextPanelActive(constants.CONTEXT_PANEL.ENDPOINTS)) {
        this.setActiveItemData(endpointsData);

        var itemToSelect = endpointsData.newItem || endpointsData.updatedItem;
        if (itemToSelect && this.data.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['editingItemData', 'selectedEndpoint'],
              itemToSelect);
            this.emitChange();

            this.closeToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      if (this.data.editingItemData) {
        this.setInData(['editingItemData', 'endpoints'], endpointsData.items);
      }

      this.emitChange();
    });
  },

  onRetrieveResourcePools: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadResourcePools()).then((result) => {
        // Transforming from associative array to array
        var resourcePools = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            let resourcePool = result[key];
            // We need these to show the graphs when displaying the list of RPs
            resourcePool.__availableMemory = utils.getCustomPropertyValue(
              resourcePool.customProperties, '__availableMemory');
            resourcePool.__cpuUsage = utils.getCustomPropertyValue(
              resourcePool.customProperties, '__cpuUsage');
            resourcePool.__endpointLink = utils.getCustomPropertyValue(
              resourcePool.customProperties, '__endpointLink');

            resourcePool.customProperties =
                            utils.getDisplayableCustomProperties(resourcePool.customProperties);
            resourcePools.push(resourcePool);
          }
        }

        var countContainerHosts = !utils.isApplicationCompute();
        // Retrieve hosts counts for the resource pools
        var countedHostsResPoolsPromises = resourcePools.map(function(pool) {
          return services.countHostsPerResourcePool(pool.documentSelfLink, countContainerHosts)
              .then(function(hostsCount) {
                pool.hostsCount = hostsCount;

                return pool;
              });
        });

        var _this = this;
        Promise.all(countedHostsResPoolsPromises)
          .then(function(countedHostsResourcePools) {
            // notify data retrieved for all resource pools
            _this.setInData(['items'], countedHostsResourcePools);
            _this.emitChange();
        });
      });
    }
  },

  onEditResourcePool: function(resourcePool) {
    this.setInData(['editingItemData', 'item'], resourcePool);
    this.emitChange();

    actions.EndpointsActions.retrieveEndpoints();
  },

  onCancelEditResourcePool: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateResourcePool: function(resourcePool) {
    services.createResourcePool(resourcePool).then((createdResourcePool) => {
      var immutableResourcePool = Immutable(createdResourcePool);

      var resourcePools = this.data.items.asMutable();
      resourcePools.push(immutableResourcePool);

      this.setInData(['items'], resourcePools);
      this.setInData(['newItem'], immutableResourcePool);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateResourcePool: function(resourcePool) {
    services.updateResourcePool(resourcePool).then((updatedResourcePool) => {
      // If the backend did not make any changes, the response will be empty
      updatedResourcePool = updatedResourcePool || resourcePool;

      var immutableResourcePool = Immutable(updatedResourcePool);

      var resourcePools = this.data.items.asMutable();

      for (var i = 0; i < resourcePools.length; i++) {
        if (resourcePools[i].documentSelfLink === updatedResourcePool.documentSelfLink) {
          resourcePools[i] = immutableResourcePool;
        }
      }

      this.setInData(['items'], resourcePools);
      this.setInData(['updatedItem'], immutableResourcePool);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteResourcePool: function(resourcePool) {
    services.deleteResourcePool(resourcePool).then(() => {
      var resourcePools = this.data.items.filter(
        (rp) => rp.documentSelfLink !== resourcePool.documentSelfLink);

      this.setInData(['items'], resourcePools);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], resourcePool);
      this.setInData(['validationErrors'], validationErrors);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
      this.setInData(['validationErrors'], null);
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    console.error(e);
    this.emitChange();
  },

  onOpenToolbarEndpoints: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.ENDPOINTS, {}, false);
    actions.EndpointsActions.retrieveEndpoints();
  },

  onCreateEndpoint: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.ENDPOINTS, {}, true);
    actions.EndpointsActions.retrieveEndpoints();
    actions.EndpointsActions.editEndpoint({});
  },

  onManageEndpoints: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.ENDPOINTS, {}, true);
    actions.EndpointsActions.retrieveEndpoints();
  },

  onCloseToolbar: function() {
    this.closeToolbar();
  }
});

export default ResourcePoolsStore;
