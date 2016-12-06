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

import PlacementZonesRowTemplate from 'components/placementzones/PlacementZonesRowTemplate.html';
import PlacementZonesRowHighlightTemplate from
  'components/placementzones/PlacementZonesRowHighlightTemplate.html';
import utils from 'core/utils';

var renderers = {
  render: function(config) {
    var placementZone = config.resourcePoolState;
    var model = {
      id: placementZone.id,
      documentId: utils.getDocumentId(placementZone.documentSelfLink),
      name: placementZone.name
    };

    if (placementZone.__cpuUsage != null) {
      model.cpuPercentage = Math.round(parseFloat(placementZone.__cpuUsage) * 100) / 100;
    }

    if (placementZone.maxMemoryBytes !== undefined &&
      placementZone.__availableMemory != null) {

      var currentMemory = placementZone.maxMemoryBytes -
        parseFloat(placementZone.__availableMemory);
      model.memoryPercentage = calculatePercentageOfTotal(0,
        placementZone.maxMemoryBytes, currentMemory
      );
    }

    var currentStorage = placementZone.minDiskCapacityBytes +
      (placementZone.maxDiskCapacityBytes - placementZone.minDiskCapacityBytes) * Math.random();
    model.storagePercentage = calculatePercentageOfTotal(placementZone.minDiskCapacityBytes,
      placementZone.maxDiskCapacityBytes,
      currentStorage);
    model.hostsCount = placementZone.hostsCount;

    if (utils.isApplicationCompute()) {
      model.hostsPath = 'compute';
    } else {
      model.hostsPath = 'hosts';
    }

    return $(PlacementZonesRowTemplate(model));
  },

  renderHighlighted: function(placementZone, $placementZoneRow, isNew,
      isUpdated, validationErrors) {
    var model = {
      placementZoneRow: $placementZoneRow.html(),
      name: placementZone.name,
      cpuPercentage: placementZone.cpuPercentage,
      memoryPercentage: placementZone.memoryPercentage,
      storagePercentage: placementZone.storagePercentage,
      hostsCount: placementZone.hostsCount,
      isNew: isNew,
      isUpdated: isUpdated,
      validationErrors: validationErrors
    };

    return $(PlacementZonesRowHighlightTemplate(model));
  }
};

var calculatePercentageOfTotal = function(min, max, current) {
  var percentage = 100;
  if (!max) {
    percentage = 0;
  } else if (max - min > 0) {
    percentage = ((current - min) / (max - min)) * 100;
  }

  return Math.round(percentage * 100) / 100;
};

export default renderers;
