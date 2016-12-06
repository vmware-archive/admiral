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

  return {
    documentSelfLink: dto.documentSelfLink,
    id: dto.id,
    name: dto.name,
    address: dto.address,
    powerState: dto.powerState,
    resourcePoolLink: dto.resourcePoolLink,
    descriptionLink: dto.descriptionLink,
    placementZoneDocumentId: dto.resourcePoolLink && utils.getDocumentId(dto.resourcePoolLink),
    connectionType: hasCustomProperties ? dto.customProperties.__adapterDockerType : null,
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

      operation.forPromise(services.loadMachines(queryOptions, false)).then((result) => {
        var documents = result.documentLinks.map((documentLink) =>
              result.documents[documentLink]);
        var nextPageLink = result.nextPageLink;
        var itemsCount = result.totalCount;
        var machines = documents.map((document) => toViewModel(document));

        this.getPlacementZones(machines).then((result) => {
          machines.forEach((machine) => {
            if (result[machine.resourcePoolLink]) {
              machine.placementZoneName =
                 result[machine.resourcePoolLink].resourcePoolState.name;
            }
          });
          return this.getDescriptions(machines);
        }).then((result) => {

          machines.forEach((machine) => {
            if (result[machine.descriptionLink]) {
              machine.cpuCount = result[machine.descriptionLink].cpuCount;
              machine.cpuMhzPerCore = result[machine.descriptionLink].cpuMhzPerCore;
              machine.memory =
                  Math.floor(result[machine.descriptionLink].totalMemoryBytes / 1048576);
            }
          });

          this.setInData(['listView', 'items'], machines);
          this.setInData(['listView', 'itemsLoading'], false);
          if (itemsCount !== undefined && itemsCount !== null) {
            this.setInData(['listView', 'itemsCount'], itemsCount);
          }
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          this.emitChange();
        });
      });
    }

    this.emitChange();
  },

  onOpenMachinesNext: function(queryOptions, nextPageLink) {
    this.setInData(['listView', 'queryOptions'], queryOptions);

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadNextPage(nextPageLink)).then((result) => {
        var documents = result.documentLinks.map((documentLink) =>
              result.documents[documentLink]);
        var nextPageLink = result.nextPageLink;
        let machines = documents.map((document) => toViewModel(document));

        this.getPlacementZones(machines).then((result) => {
          machines.forEach((machine) => {
            if (result[machine.resourcePoolLink]) {
              machine.placementZoneName =
                 result[machine.resourcePoolLink].resourcePoolState.name;
            }
          });
          return this.getDescriptions(machines);
        }).then((result) => {

          machines.forEach((machine) => {
            if (result[machine.descriptionLink]) {
              machine.cpuCount = result[machine.descriptionLink].cpuCount;
              machine.cpuMhzPerCore = result[machine.descriptionLink].cpuMhzPerCore;
              machine.memory =
                  Math.floor(result[machine.descriptionLink].totalMemoryBytes / 1048576);
            }
          });

          this.setInData(['listView', 'items'],
              utils.mergeDocuments(this.data.listView.items.asMutable(), machines));
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          this.emitChange();
        });
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
  },

  getPlacementZones: function(machines) {
    let placementZones = utils.getIn(this.data, ['listView', 'placementZones']) || {};
    let resourcePoolLinks = machines.filter((machine) =>
        machine.resourcePoolLink).map((machine) => machine.resourcePoolLink);
    let links = [...new Set(resourcePoolLinks)].filter((link) =>
        !placementZones.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(placementZones);
    }
    return services.loadPlacementZones(links).then((newPlacementZones) => {
      this.setInData(['listView', 'placementZones'],
          $.extend({}, placementZones, newPlacementZones));
      return utils.getIn(this.data, ['listView', 'placementZones']);
    });
  },

  getDescriptions: function(machines) {
    let descriptions = utils.getIn(this.data, ['listView', 'descriptions']) || {};
    let descriptionLinks = machines.filter((machine) =>
        machine.descriptionLink).map((machine) => machine.descriptionLink);
    let links = [...new Set(descriptionLinks)].filter((link) =>
        !descriptions.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(descriptions);
    }
    return services.loadHostDescriptions(links).then((newDescriptions) => {
      this.setInData(['listView', 'descriptions'], $.extend({}, descriptions, newDescriptions));
      return utils.getIn(this.data, ['listView', 'descriptions']);
    });
  }
});

export default MachinesStore;
