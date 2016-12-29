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
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import services from 'core/services';
import utils from 'core/utils';

const OPERATION = {
  LIST: 'list'
};

let EnvironmentsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [EnvironmentsActions],

  onOpenEnvironments: function(queryOptions) {
    this.setInData(['listView', 'queryOptions'], queryOptions);
    this.setInData(['editingItemData'], null);
    this.setInData(['validationErrors'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['listView', 'itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadEnvironments()).then((result) => {
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

  onOpenAddEnvironment: function() {
    this.setInData(['editingItemData', 'item'], {});
    this.emitChange();
  },

  onEditEnvironment: function(environmentId) {
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

  onCancelEditEnvironment: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateEnvironment: function(model, tags) {
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

  onUpdateEnvironment: function(model, tags) {
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

  onDeleteEnvironment: function(environment) {
    services.deleteEnvironment(environment).then(() => {
      var environments = this.data.listView.items.asMutable().filter((item) =>
          item.documentSelfLink !== environment.documentSelfLink);

      this.setInData(['listView', 'items'], Immutable(environments));
      this.setInData(['listView', 'itemsCount'], this.data.listView.itemsCount - 1);
      this.emitChange();
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    this.setInData(['editingItemData', 'saving'], false);
    console.error(e);
    this.emitChange();
  },

  onEditEnvironmentProperty: function(property) {
    this.setInData(['editingItemData', 'property'], property);
    this.emitChange();
  },

  onCancelEditEnvironmentProperty: function() {
    this.setInData(['editingItemData', 'property'], null);
    this.emitChange();
  },

  onUpdateEnvironmentProperties: function(properties) {
    this.setInData(['editingItemData', 'item', 'properties'], properties);
    this.setInData(['editingItemData', 'property'], null);
    this.emitChange();
  }

});

export default EnvironmentsStore;

