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
import utils from 'core/utils';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';

const OPERATION = {
  LIST: 'LIST',
  DETAILS: 'DETAILS'
};

let toViewModel = function(dto) {
  var customProperties = [];
  let hasCustomProperties = dto.customProperties && dto.customProperties !== null;
  if (hasCustomProperties) {
    for (var key in dto.customProperties) {
      if (dto.customProperties.hasOwnProperty(key)) {
        customProperties.push({
          name: key,
          value: dto.customProperties[key]
        });
      }
    }
  }

  var memoryUsagePct;
  if (hasCustomProperties && dto.customProperties.__MemTotal
      && dto.customProperties.__MemAvailable) {
    var memoryUsage = dto.customProperties.__MemTotal - dto.customProperties.__MemAvailable;
    memoryUsagePct = (memoryUsage / dto.customProperties.__MemTotal) * 100;
    memoryUsagePct = Math.round(memoryUsagePct * 100) / 100;
  }

  var cpuUsagePct;
  if (hasCustomProperties && dto.customProperties.__CpuUsage) {
    cpuUsagePct = Math.round(dto.customProperties.__CpuUsage * 100) / 100;
  }

  return {
    documentSelfLink: dto.documentSelfLink,
    id: dto.id,
    name: dto.name,
    address: dto.address,
    powerState: dto.powerState,
    resourcePoolDocumentId: dto.resourcePoolLink && utils.getDocumentId(dto.resourcePoolLink),
    connectionType: hasCustomProperties ? dto.customProperties.__adapterDockerType : null,
    memoryPercentage: memoryUsagePct,
    cpuPercentage: cpuUsagePct,
    customProperties: customProperties
  };
};

let MachinesStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
  },

  listenables: [actions.MachineActions],

  onOpenMachines: function(queryOptions, forceReload) {
    var items = utils.getIn(this.data, ['listView', 'items']);
    if (!forceReload && items) {
      return;
    }

    this.setInData(['selectedItem'], null);
    this.setInData(['selectedItemDetails'], null);
    this.setInData(['listView', 'queryOptions'], queryOptions);

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadHosts(queryOptions, false))
        .then((result) => {
          // Transforming to the model of the view
          var documents = result.documents;
          var nextPageLink = result.nextPageLink;

          // TODO: temporary client side filter
          var machines = [];
          for (var key in documents) {
            if (documents.hasOwnProperty(key)) {
              var document = documents[key];
              if (document.customProperties &&
                  document.customProperties.computeType) {
                machines.push(toViewModel(document));
              }
            }
          }

          this.setInData(['listView', 'items'], machines);
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'itemsCount'], result.itemsCount);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          this.emitChange();
        });
    }

    this.emitChange();
  },

  onOpenMachineDetails: function(machineId) {
    // If switching between views, there will be a short period that we show old data,
    // until the new one is loaded.
    var currentItemDetailsCursor = this.selectFromData(['selectedItemDetails']);
    currentItemDetailsCursor.merge({
      documentSelfLink: '/resources/compute/' + machineId,
      logsLoading: true
    });

    var currentItemCursor = this.selectFromData(['selectedItem']);
    currentItemCursor.merge({
      documentSelfLink: '/resources/compute/' + machineId
    });
    this.emitChange();

    this.emitChange();

    this.cancelOperations(OPERATION.DETAILS);
    var operation = this.requestCancellableOperation(OPERATION.DETAILS);
    operation.forPromise(services.loadHost(machineId))
      .then((machine) => {
        machine = toViewModel(machine);

        currentItemCursor.merge(machine);
        currentItemDetailsCursor.setIn(['instance'], machine);

        this.emitChange();
      });
  }
});

export default MachinesStore;
