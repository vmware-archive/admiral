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

const OPERATION = {
  LIST: 'list'
};

function enhanceConfig(config) {
  var cpuUsage = utils.getCustomPropertyValue(
      config.resourcePoolState.customProperties, '__cpuUsage');
  if (cpuUsage != null) {
    config.cpuPercentage = Math.round(parseFloat(cpuUsage) * 100) / 100;
  }
  var availableMemory = utils.getCustomPropertyValue(
      config.resourcePoolState.customProperties, '__availableMemory');
  if (config.resourcePoolState.maxMemoryBytes !== undefined && availableMemory != null) {
    var currentMemory = config.resourcePoolState.maxMemoryBytes -
        parseFloat(availableMemory);
    config.memoryPercentage = utils.calculatePercentageOfTotal(0,
        config.resourcePoolState.maxMemoryBytes, currentMemory);
  }
  var minCapacity = config.resourcePoolState.minDiskCapacityBytes;
  var maxCapacity = config.resourcePoolState.maxDiskCapacityBytes;
  config.storagePercentage = utils.calculatePercentageOfTotal(minCapacity,
      maxCapacity, minCapacity + (maxCapacity - minCapacity) * Math.random());
  config.endpointLink = utils.getCustomPropertyValue(
      config.resourcePoolState.customProperties, '__endpointLink');
  config.name = config.resourcePoolState.name;
  return config;
}

let PlacementZonesStore = Reflux.createStore({
  listenables: [actions.PlacementZonesActions],
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
    this.setInData(['deleteConfirmationLoading'], false);
  },

  onRetrievePlacementZones: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadPlacementZones()).then((result) => {
        // Transforming from associative array to array
        var processedResult = Object.values(result).map((config) => {
          config = enhanceConfig(config);
          config.resourcePoolState.customProperties =
            utils.getDisplayableCustomProperties(config.resourcePoolState.customProperties);
          return config;
        });

        var tagPromises = Object.values(processedResult).map((config) => {
          if (config.epzState &&
              config.epzState.tagLinksToMatch && config.epzState.tagLinksToMatch.length) {
            return services.loadTags(config.epzState.tagLinksToMatch).then((tags) => {
              config.tags = Object.values(tags);
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
            return services.countHostsPerPlacementZone(config.resourcePoolState.documentSelfLink,
                countContainerHosts, countOnlyComputes).then(function(hostsCount) {
                  config.hostsCount = hostsCount;
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

  onEditPlacementZone: function(config) {
    if (config.endpointLink) {
      services.loadEndpoint(config.endpointLink).then((endpoint) => {
        this.setInData(['editingItemData', 'item'], Immutable($.extend({
          endpoint
        }, config)));
        this.emitChange();
      });
    } else {
      this.setInData(['editingItemData', 'item'], config);
      this.emitChange();
    }
  },

  onCancelEditPlacementZone: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreatePlacementZone: function(config, tags) {
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((createdTags) => {
      if (config.epzState) {
        config.epzState = $.extend(config.epzState, {
          tagLinksToMatch: createdTags.map((tag) => tag.documentSelfLink)
        });
      }
      return services.createPlacementZone(config);
    }).then((createdConfig) => {

      createdConfig.hostsCount = 0;
      createdConfig.cpuPercentage = 0;
      createdConfig.memoryPercentage = 0;
      createdConfig.storagePercentage = 0;
      createdConfig = enhanceConfig(createdConfig);
      if (tags.length) {
        createdConfig.tags = tags;
      }

      var immutableConfig = Immutable(createdConfig);

      var configs = this.data.items.asMutable();
      configs.push(immutableConfig);

      this.setInData(['items'], configs);
      this.setInData(['newItem'], immutableConfig);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      setTimeout(() => {
        this.setInData(['newItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

    }).catch(this.onGenericEditError);
  },

  onUpdatePlacementZone: function(config, tags) {
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((updatedTags) => {
      if (config.epzState) {
        config.epzState = $.extend(config.epzState, {
          resourcePoolLink: config.resourcePoolState.documentSelfLink,
          tagLinksToMatch: updatedTags.map((tag) => tag.documentSelfLink)
        });
      }
      return services.updatePlacementZone(config);
    }).then((updatedConfig) => {
      // If the backend did not make any changes, the response will be empty
      updatedConfig.resourcePoolState = updatedConfig.resourcePoolState || config.resourcePoolState;
      updatedConfig.epzState = updatedConfig.epzState || config.epzState;
      updatedConfig.tags = tags;
      updatedConfig = enhanceConfig(updatedConfig);

      var configs = this.data.items.asMutable();

      for (var i = 0; i < configs.length; i++) {
        if (configs[i].documentSelfLink === updatedConfig.documentSelfLink) {
          updatedConfig.hostsCount = configs[i].hostsCount;
          configs[i] = Immutable(updatedConfig);

          this.setInData(['items'], configs);
          this.setInData(['updatedItem'], configs[i]);
          this.setInData(['editingItemData'], null);
          this.emitChange();
          break;
        }
      }

      // After we notify listeners, the updated item is no logner actual
      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

    }).catch(this.onGenericEditError);
  },

  onDeletePlacementZone: function(config) {
    this.setInData(['deleteConfirmationLoading'], true);
    this.emitChange();
    services.deletePlacementZone(config).then(() => {
      var configs = this.data.items.filter((cfg) =>
          cfg.documentSelfLink !== config.documentSelfLink);

      this.setInData(['items'], configs);
      this.setInData(['deleteConfirmationLoading'], false);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], config);
      this.setInData(['validationErrors'], validationErrors);
      this.emitChange();

      // After we notify listeners, the updated item is no logner actual
      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.setInData(['validationErrors'], null);
        this.setInData(['deleteConfirmationLoading'], false);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    this.setInData(['editingItemData', 'saving'], false);
    console.error(e);
    this.emitChange();
  }
});

export default PlacementZonesStore;
