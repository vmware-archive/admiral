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
  config.placementZoneType = utils.getCustomPropertyValue(
      config.resourcePoolState.customProperties, '__placementZoneType');
  config.dto = {
    epzState: config.epzState,
    resourcePoolState: config.resourcePoolState
  };
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

        let configs = Object.values(processedResult);
        var tagsPromises = configs.map((config) => {
          if (config.resourcePoolState &&
              config.resourcePoolState.tagLinks && config.resourcePoolState.tagLinks.length) {
            return services.loadTags(config.resourcePoolState.tagLinks).then((tags) => {
              config.tags = Object.values(tags);
              return config;
            });
          } else {
            config.tags = [];
            return Promise.resolve(config);
          }
        });

        var tagsToMatchPromises = configs.map((config) => {
          if (config.epzState &&
              config.epzState.tagLinksToMatch && config.epzState.tagLinksToMatch.length) {
            return services.loadTags(config.epzState.tagLinksToMatch).then((tagsToMatch) => {
              config.tagsToMatch = Object.values(tagsToMatch);
              return config;
            });
          } else {
            config.tagsToMatch = [];
            return Promise.resolve(config);
          }
        });

        Promise.all([...tagsPromises, ...tagsToMatchPromises]).then(() => {
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

  onCreatePlacementZone: function(config, tags, tagsToMatch) {
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    let tagsPromises = [];
    tags.forEach((tag) => {
      tagsPromises.push(services.createTag(tag));
    });

    let tagsToMatchPromises = [];
    tagsToMatch.forEach((tag) => {
      tagsToMatchPromises.push(services.createTag(tag));
    });

    Promise.all(tagsPromises).then((createdTags) => {
      config.resourcePoolState.tagLinks = createdTags.map((tag) => tag.documentSelfLink);
      return Promise.all(tagsToMatchPromises);
    }).then((createdTagsToMatch) => {
      if (config.epzState) {
        config.epzState = $.extend(config.epzState, {
          tagLinksToMatch: createdTagsToMatch.map((tag) => tag.documentSelfLink)
        });
      }
      return services.createPlacementZone(config);
    }).then((createdConfig) => {

      createdConfig.hostsCount = 0;
      createdConfig.cpuPercentage = 0;
      createdConfig.memoryPercentage = 0;
      createdConfig.storagePercentage = 0;
      createdConfig.tags = tags;
      createdConfig.tagsToMatch = tagsToMatch;
      createdConfig = enhanceConfig(createdConfig);

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

  onUpdatePlacementZone: function(config, tags, tagsToMatch) {
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    let tagsPromises = [];
    tags.forEach((tag) => {
      tagsPromises.push(services.createTag(tag));
    });

    let tagsToMatchPromises = [];
    tagsToMatch.forEach((tag) => {
      tagsToMatchPromises.push(services.createTag(tag));
    });

    Promise.all(tagsPromises).then((updatedTags) => {
      config.resourcePoolState.tagLinks = updatedTags.map((tag) => tag.documentSelfLink);
      return Promise.all(tagsToMatchPromises);
    }).then((updatedTagsToMatch) => {
      if (config.epzState) {
        config.epzState = $.extend(config.epzState, {
          resourcePoolLink: config.resourcePoolState.documentSelfLink,
          tagLinksToMatch: updatedTagsToMatch.map((tag) => tag.documentSelfLink)
        });
      }
      return services.updatePlacementZone($.extend(true, {}, config.dto, config));
    }).then((updatedConfig) => {
      // If the backend did not make any changes, the response will be empty
      updatedConfig.resourcePoolState = updatedConfig.resourcePoolState || config.resourcePoolState;
      updatedConfig.epzState = updatedConfig.epzState || config.epzState;
      updatedConfig.tags = tags;
      updatedConfig.tagsToMatch = tagsToMatch;
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
