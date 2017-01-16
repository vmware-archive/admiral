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

import { EndpointsActions, EnvironmentsActions, NavigationActions } from 'actions/Actions';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import EndpointsStore from 'stores/EndpointsStore';
import constants from 'core/constants';
import services from 'core/services';
import utils from 'core/utils';

const OPERATION = {
  LIST: 'list'
};

function isContextPanelActive(name) {
  var activeItem = this.data.editingItemData.contextView &&
      this.data.editingItemData.contextView.activeItem;
  return activeItem && activeItem.name === name;
}

function onOpenToolbarItem(name, data, shouldSelectAndComplete) {
  var contextViewData = {
    expanded: true,
    activeItem: {
      name: name,
      data: data
    },
    shouldSelectAndComplete: shouldSelectAndComplete
  };

  this.setInData(['editingItemData', 'contextView'], contextViewData);
  this.emitChange();
}

let EnvironmentsStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],
  init() {
    EndpointsStore.listen((endpointsData) => {
      if (!this.data.editingItemData) {
        return;
      }

      let endpoints = (endpointsData.items || []).map((item) =>
        $.extend({
          iconSrc: `image-assets/endpoints/${item.endpointType}.png`
        }, item));
      this.setInData(['editingItemData', 'endpoints'], endpoints);

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.ENDPOINTS)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'],
          endpointsData);

        let itemToSelect = endpointsData.newItem || endpointsData.updatedItem;
        if (itemToSelect && this.data.editingItemData.contextView.shouldSelectAndComplete) {
          itemToSelect = endpoints.find((item) =>
              item.documentSelfLink === itemToSelect.documentSelfLink);
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            console.log(3, itemToSelect);
            console.log(4, endpoints.find((item) => item === itemToSelect));
            this.setInData(['editingItemData', 'item', 'endpoint'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });
  },

  listenables: [EnvironmentsActions],

  onOpenEnvironments(queryOptions) {
    this.setInData(['listView', 'queryOptions'], queryOptions);
    this.setInData(['editingItemData'], null);
    this.setInData(['validationErrors'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['listView', 'itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadEnvironments(queryOptions)).then((result) => {
        let nextPageLink = result.nextPageLink;
        let itemsCount = result.totalCount;

        Promise.all(result.documentLinks.map((documentLink) =>
          services.loadEnvironment(utils.getDocumentId(documentLink))
        )).then((environments) => {
          this.setInData(['listView', 'items'], environments);
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          if (itemsCount !== undefined && itemsCount !== null) {
            this.setInData(['listView', 'itemsCount'], itemsCount);
          }
          this.emitChange();
        });
      });
    }
  },

  onOpenAddEnvironment() {
    EndpointsActions.retrieveEndpoints();

    this.setInData(['editingItemData', 'item'], {});
    this.emitChange();
  },

  onEditEnvironment(environmentId) {
    services.loadEnvironment(environmentId).then((document) => {
      var promises = [];

      if (document.endpointLink) {
        promises.push(
            services.loadEndpoint(document.endpointLink).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.tagLinks && document.tagLinks.length) {
        promises.push(
            services.loadTags(document.tagLinks).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      Promise.all(promises).then(([endpoint, tags]) => {
        if (document.endpointLink && endpoint) {
          document.endpoint = endpoint;
        }
        document.tags = tags ? Object.values(tags) : [];

        this.setInData(['editingItemData', 'item'], Immutable(document));
        this.emitChange();
      });

    }).catch(this.onGenericEditError);

    EndpointsActions.retrieveEndpoints();

    this.setInData(['editingItemData', 'item'], {});
    this.emitChange();
  },

  onCancelEditEnvironment() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateEnvironment(model, tags) {
    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((updatedTags) => {
      let data = $.extend({}, model, {
        tagLinks: [...new Set(updatedTags.map((tag) => tag.documentSelfLink))]
      });
      Promise.all([
        services.createComputeProfile(data.computeProfile),
        services.createNetworkProfile(data.networkProfile),
        services.createStorageProfile(data.storageProfile)
      ]).then(([computeProfile, networkProfile, storageProfile]) => {
        data = $.extend(data, {
          computeProfileLink: computeProfile.documentSelfLink,
          networkProfileLink: networkProfile.documentSelfLink,
          storageProfileLink: storageProfile.documentSelfLink
        });
        return services.createEnvironment(data);
      }).then(() => {
        NavigationActions.openEnvironments();
        this.setInData(['editingItemData'], null);
        this.emitChange();
      }).catch(this.onGenericEditError);
    });

    this.setInData(['editingItemData', 'item'], model);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();
  },

  onUpdateEnvironment(model, tags) {
    Promise.all(tags.map((tag) => services.loadTag(tag.key, tag.value))).then((result) => {
      return Promise.all(tags.map((tag, i) =>
        result[i] ? Promise.resolve(result[i]) : services.createTag(tag)));
    }).then((updatedTags) => {
      let data = $.extend({}, model, {
        tagLinks: [...new Set(updatedTags.map((tag) => tag.documentSelfLink))]
      });
      Promise.all([
        services.updateComputeProfile(data.computeProfile),
        services.updateNetworkProfile(data.networkProfile),
        services.updateStorageProfile(data.storageProfile),
        services.updateEnvironment(data)
      ]).then(() => {
        NavigationActions.openEnvironments();
        this.setInData(['editingItemData'], null);
        this.emitChange();
      }).catch(this.onGenericEditError);
    });

    this.setInData(['editingItemData', 'item'], model);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();
  },

  onDeleteEnvironment(environment) {
    services.deleteEnvironment(environment).then(() => {
      var environments = this.data.listView.items.asMutable().filter((item) =>
          item.documentSelfLink !== environment.documentSelfLink);

      this.setInData(['listView', 'items'], Immutable(environments));
      this.setInData(['listView', 'itemsCount'], this.data.listView.itemsCount - 1);
      this.emitChange();
    });
  },

  onGenericEditError(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    this.setInData(['editingItemData', 'saving'], false);
    console.error(e);
    this.emitChange();
  },

  onOpenToolbarEndpoints() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
      EndpointsStore.getData(), false);
  },

  onCloseToolbar() {
    if (!this.data.editingItemData) {
      this.closeToolbar();
    } else {
      var contextViewData = {
        expanded: false,
        activeItem: null
      };
      this.setInData(['editingItemData', 'contextView'], contextViewData);
      this.emitChange();
    }
  },

  onCreateEndpoint() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
        EndpointsStore.getData(), true);
    EndpointsActions.editEndpoint({});
  },

  onManageEndpoints() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
        EndpointsStore.getData(), true);
  }
});

export default EnvironmentsStore;

