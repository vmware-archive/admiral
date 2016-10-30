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
import links from 'core/links';
import constants from 'core/constants';
import ResourcePoolsStore from 'stores/ResourcePoolsStore';

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

  var epzs = [];
  for (var i = 0; i < customProperties.length; i++) {
    if (customProperties[i].name.startsWith(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX)) {
        let epzId = customProperties[i].name.slice(constants.CUSTOM_PROPS.EPZ_NAME_PREFIX.length);
        epzs.push({
           epzDocumentId: epzId,
           epzLink: links.RESOURCE_POOLS + '/' + epzId
        });
    }
  }

  return {
    dto: dto,
    documentSelfLink: dto.documentSelfLink,
    selfLinkId: utils.getDocumentId(dto.documentSelfLink),
    id: dto.id,
    name: dto.name,
    powerState: dto.powerState,
    descriptionLink: dto.descriptionLink,
    resourcePoolLink: dto.resourcePoolLink,
    resourcePoolDocumentId: dto.resourcePoolLink && utils.getDocumentId(dto.resourcePoolLink),
    epzs: epzs,
    connectionType: hasCustomProperties ? dto.customProperties.__adapterDockerType : null,
    customProperties: customProperties,
    computeType: hasCustomProperties ? dto.customProperties.__computeType : null
  };
};

let onOpenToolbarItem = function(name, data, shouldSelectAndComplete) {
  var contextViewData = {
    expanded: true,
    activeItem: {
      name: name,
      data: data
    },
    shouldSelectAndComplete: shouldSelectAndComplete
  };

  this.setInData(['computeEditView', 'contextView'], contextViewData);
  this.emitChange();
};

let isContextPanelActive = function(name) {
  var activeItem = this.data.computeEditView.contextView &&
      this.data.computeEditView.contextView.activeItem;
  return activeItem && activeItem.name === name;
};

let ComputeStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {

    ResourcePoolsStore.listen((resourcePoolsData) => {
      if (!this.data.computeEditView) {
        return;
      }

      this.setInData(['computeEditView', 'resourcePools'], resourcePoolsData.items);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS)) {
        this.setInData(['computeEditView', 'contextView', 'activeItem', 'data'],
          resourcePoolsData);

        var itemToSelect = resourcePoolsData.newItem || resourcePoolsData.updatedItem;
        if (itemToSelect && this.data.computeEditView.contextView.shouldSelectAndComplete) {
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['computeEditView', 'resourcePool'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });
  },

  listenables: [actions.ComputeActions, actions.ComputeContextToolbarActions],

  onOpenCompute: function(queryOptions, forceReload) {
    var items = utils.getIn(this.data, ['listView', 'items']);
    if (!forceReload && items) {
      return;
    }

    this.setInData(['computeEditView'], null);
    this.setInData(['selectedItem'], null);
    this.setInData(['selectedItemDetails'], null);
    this.setInData(['listView', 'queryOptions'], queryOptions);

    var operation = this.requestCancellableOperation(OPERATION.LIST, queryOptions);

    if (operation) {
      this.cancelOperations(OPERATION.DETAILS);
      this.setInData(['listView', 'itemsLoading'], true);

      operation.forPromise(services.loadCompute(queryOptions)).then((result) => {
        // Transforming to the model of the view
        var documents = result.documents;
        var nextPageLink = result.nextPageLink;

        // TODO: temporary client side filter
        var compute = [];
        for (var key in documents) {
          if (documents.hasOwnProperty(key)) {
            var document = documents[key];
            compute.push(toViewModel(document));
          }
        }

        this.getResourcePools(compute).then((result) => {
          compute.forEach((compute) => {
            compute.epzs.forEach((epz) => {
              if (result[epz.epzLink]) {
                epz.epzName = result[epz.epzLink].resourcePoolState.name;
              }
            });
          });
          return this.getDescriptions(compute);
        }).then((result) => {

          compute.forEach((compute) => {
            if (result[compute.descriptionLink]) {
              compute.cpuCount = result[compute.descriptionLink].cpuCount;
              compute.cpuMhzPerCore = result[compute.descriptionLink].cpuMhzPerCore;
              compute.memory =
                  Math.floor(result[compute.descriptionLink].totalMemoryBytes / 1048576);
            }
          });

          this.setInData(['listView', 'items'], compute);
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'itemsCount'], result.itemsCount);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          this.emitChange();
        });
      });
    }

    this.emitChange();
  },

  onEditCompute: function(computeId) {

    // load host data from backend
    services.loadHost(computeId).then((document) => {
      let computeModel = toViewModel(document);

      actions.ResourcePoolsActions.retrieveResourcePools();

      var promises = [];

      if (document.resourcePoolLink) {
        promises.push(
            services.loadResourcePool(document.resourcePoolLink).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.tagLinks && document.tagLinks.length) {
        promises.push(
            services.loadTags(document.tagLinks).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      Promise.all(promises).then(([config, tags]) => {
        if (document.resourcePoolLink && config) {
          computeModel.resourcePool = config.resourcePoolState;
        }
        computeModel.tags = tags ? Object.values(tags) : [];

        this.setInData(['computeEditView'], computeModel);
        this.emitChange();
      });

    }).catch(this.onGenericEditError);

    this.setInData(['computeEditView'], {});
    this.emitChange();
  },

  onUpdateCompute: function(computeModel, tags) {
    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((updatedTags) => {
      let computeData = $.extend({}, computeModel.dto, {
        resourcePoolLink: computeModel.resourcePoolLink,
        tagLinks: [...new Set(updatedTags.map((tag) => tag.documentSelfLink))]
      });
      services.updateCompute(computeModel.selfLinkId, computeData).then(() => {
        actions.NavigationActions.openCompute();
        this.setInData(['computeEditView'], null);
        this.setInData(['computeEditView', 'isSavingCompute'], false);
        this.emitChange();
      }).catch(this.onGenericEditError);
    });
    this.setInData(['computeEditView', 'isSavingCompute'], true);
    this.emitChange();
  },

  onOpenToolbarResourcePools: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
      ResourcePoolsStore.getData(), false);
  },

  onCloseToolbar: function() {
    if (!this.data.computeEditView) {

      this.closeToolbar();
    } else {

      var contextViewData = {
        expanded: false,
        activeItem: null
      };

      this.setInData(['computeEditView', 'contextView'], contextViewData);
      this.emitChange();
    }
  },

  onCreateResourcePool: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
      ResourcePoolsStore.getData(), true);
    actions.ResourcePoolsActions.editResourcePool();
  },

  onManageResourcePools: function() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.RESOURCE_POOLS,
      ResourcePoolsStore.getData(), true);
  },

  getResourcePools: function(compute) {
    let resourcePools = utils.getIn(this.data, ['listView', 'resourcePools']) || {};
    let resourcePoolLinks = [];
    compute.forEach((compute) => {
      compute.epzs.forEach((epz) => resourcePoolLinks.push(epz.epzLink));
    });
    let links = [...new Set(resourcePoolLinks)].filter((link) =>
        !resourcePools.hasOwnProperty(link));
    if (links.length === 0) {
      return Promise.resolve(resourcePools);
    }
    return services.loadResourcePools(links).then((newResourcePools) => {
      this.setInData(['listView', 'resourcePools'], $.extend({}, resourcePools, newResourcePools));
      return utils.getIn(this.data, ['listView', 'resourcePools']);
    });
  },

  getDescriptions: function(compute) {
    let descriptions = utils.getIn(this.data, ['listView', 'descriptions']) || {};
    let descriptionLinks = compute.filter((compute) =>
        compute.descriptionLink).map((compute) => compute.descriptionLink);
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

export default ComputeStore;
