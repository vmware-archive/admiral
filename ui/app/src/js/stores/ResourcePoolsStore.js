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
        var processedResult = Object.values(result).map((config) => {
          // We need these to show the graphs when displaying the list of RPs
          config.resourcePoolState.__availableMemory = utils.getCustomPropertyValue(
            config.resourcePoolState.customProperties, '__availableMemory');
          config.resourcePoolState.__cpuUsage = utils.getCustomPropertyValue(
            config.resourcePoolState.customProperties, '__cpuUsage');
          config.resourcePoolState.__endpointLink = utils.getCustomPropertyValue(
            config.resourcePoolState.customProperties, '__endpointLink');

          config.resourcePoolState.customProperties =
            utils.getDisplayableCustomProperties(config.resourcePoolState.customProperties);
          return config;
        });

        var tagPromises = Object.values(processedResult).map((config) => {
          if (config.epzState &&
              config.epzState.tagLinksToMatch && config.epzState.tagLinksToMatch.length) {
            return services.loadTags(config.epzState.tagLinksToMatch).then((tags) => {
              config.resourcePoolState.__tags = Object.values(tags);
              return config;
            });
          } else {
            return Promise.resolve(config);
          }
        });

        Promise.all(tagPromises).then((configs) => {

          var countContainerHosts = !utils.isApplicationCompute();
          var countOnlyComputes = utils.isApplicationCompute() ? true : undefined;
          // Retrieve hosts counts for the resource pools
          var countedHostsResPoolsPromises = Object.values(configs).map(function(config) {
            return services.countHostsPerResourcePool(config.resourcePoolState.documentSelfLink,
                countContainerHosts, countOnlyComputes).then(function(hostsCount) {
                  config.resourcePoolState.hostsCount = hostsCount;
                  return config;
                });
          });

          Promise.all(countedHostsResPoolsPromises).then((configs) => {
              // notify data retrieved for all resource pools
              this.setInData(['items'], configs);
              this.emitChange();
          });
        });
      });
    }
  },

  onEditResourcePool: function(config) {
    this.setInData(['editingItemData', 'item'], config);
    this.emitChange();

    actions.EndpointsActions.retrieveEndpoints();
  },

  onCancelEditResourcePool: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateResourcePool: function(config, tags) {
    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((createdTags) => {
      if (tags.length !== 0) {
        config.epzState = {
          tagLinksToMatch: createdTags.map((tag) => tag.documentSelfLink)
        };
      }
      return services.createResourcePool(config);
    }).then((createdConfig) => {

      createdConfig.resourcePoolState.__availableMemory = utils.getCustomPropertyValue(
          createdConfig.resourcePoolState.customProperties, '__availableMemory');
      createdConfig.resourcePoolState.__cpuUsage = utils.getCustomPropertyValue(
          createdConfig.resourcePoolState.customProperties, '__cpuUsage');
      createdConfig.resourcePoolState.__endpointLink = utils.getCustomPropertyValue(
          createdConfig.resourcePoolState.customProperties, '__endpointLink');
      if (tags.length) {
        createdConfig.resourcePoolState.__tags = tags;
      }

      var immutableConfig = Immutable(createdConfig);

      var configs = this.data.items.asMutable();
      configs.push(immutableConfig);

      this.setInData(['items'], configs);
      this.setInData(['newItem'], immutableConfig);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateResourcePool: function(config, tags) {
    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((updatedTags) => {
      if (config.epzState) {
        config.epzState.tagLinksToMatch = updatedTags.map((tag) => tag.documentSelfLink);
      } else if (tags.length !== 0) {
        config.epzState = {
          resourcePoolLink: config.resourcePoolState.documentSelfLink,
          tagLinksToMatch: updatedTags.map((tag) => tag.documentSelfLink)
        };
      }
      return services.updateResourcePool(config);
    }).then((updatedConfig) => {
      // If the backend did not make any changes, the response will be empty
      updatedConfig.resourcePoolState = updatedConfig.resourcePoolState || config.resourcePoolState;
      updatedConfig.epzState = updatedConfig.epzState || config.epzState;

      var configs = this.data.items.asMutable();

      for (var i = 0; i < configs.length; i++) {
        if (configs[i].documentSelfLink === updatedConfig.documentSelfLink) {
          updatedConfig.resourcePoolState.__availableMemory = utils.getCustomPropertyValue(
              updatedConfig.resourcePoolState.customProperties, '__availableMemory');
          updatedConfig.resourcePoolState.__cpuUsage = utils.getCustomPropertyValue(
              updatedConfig.resourcePoolState.customProperties, '__cpuUsage');
          updatedConfig.resourcePoolState.__endpointLink = utils.getCustomPropertyValue(
              updatedConfig.resourcePoolState.customProperties, '__endpointLink');
          updatedConfig.resourcePoolState.__tags = tags;
          updatedConfig.resourcePoolState.hostsCount = configs[i].resourcePoolState.hostsCount;

          configs[i] = Immutable(updatedConfig);

          this.setInData(['items'], configs);
          this.setInData(['updatedItem'], configs[i]);
          this.setInData(['editingItemData'], null);
          this.emitChange();
          break;
        }
      }

      // After we notify listeners, the updated item is no logner actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteResourcePool: function(config) {
    services.deleteResourcePool(config).then(() => {
      var configs = this.data.items.filter(
        (cfg) => cfg.documentSelfLink !== config.documentSelfLink);

      this.setInData(['items'], configs);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], config);
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
