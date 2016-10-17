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

import ResourcePoolsRowTemplate from 'ResourcePoolsRowTemplate';
import ResourcePoolsRowHighlightTemplate from 'ResourcePoolsRowHighlightTemplate';
import utils from 'core/utils';

var renderers = {
  render: function(config) {
    var resourcePool = config.resourcePoolState;
    var model = {
      id: resourcePool.id,
      documentId: utils.getDocumentId(resourcePool.documentSelfLink),
      name: resourcePool.name
    };

    if (resourcePool.__cpuUsage != null) {
      model.cpuPercentage = parseFloat(resourcePool.__cpuUsage);
    }

    if (resourcePool.maxMemoryBytes !== undefined &&
      resourcePool.__availableMemory != null) {

      var currentMemory = resourcePool.maxMemoryBytes -
        parseFloat(resourcePool.__availableMemory);
      model.memoryPercentage = calculatePercentageOfTotal(0,
        resourcePool.maxMemoryBytes, currentMemory
      );
    }

    var currentStorage = resourcePool.minDiskCapacityBytes +
      (resourcePool.maxDiskCapacityBytes - resourcePool.minDiskCapacityBytes) * Math.random();
    model.storagePercentage = calculatePercentageOfTotal(resourcePool.minDiskCapacityBytes,
      resourcePool.maxDiskCapacityBytes,
      currentStorage);
    model.hostsCount = resourcePool.hostsCount;

    if (utils.isApplicationCompute()) {
      model.hostsPath = 'compute';
    } else {
      model.hostsPath = 'hosts';
    }

    return $(ResourcePoolsRowTemplate(model));
  },

  renderHighlighted: function(resourcePool, $resourcePoolRow, isNew, isUpdated, validationErrors) {
    var model = {
      resourcePoolRow: $resourcePoolRow.html(),
      name: resourcePool.name,
      cpuPercentage: resourcePool.cpuPercentage,
      memoryPercentage: resourcePool.memoryPercentage,
      storagePercentage: resourcePool.storagePercentage,
      hostsCount: resourcePool.hostsCount,
      isNew: isNew,
      isUpdated: isUpdated,
      validationErrors: validationErrors
    };

    return $(ResourcePoolsRowHighlightTemplate(model));
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
