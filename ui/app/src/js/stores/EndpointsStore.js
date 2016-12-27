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

import { EndpointsActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const ENUMERATION_RETRIES = 20;
const OPERATION = {
  LIST: 'list'
};

function verifyEnumeration(endpoint, retries, callback) {
  services.searchCompute(endpoint.resourcePoolLink, '').then((result) => {
    setTimeout(() => {
      if (result.totalCount !== 0 || retries === 0) {
        callback(result);
      } else {
        verifyEnumeration(endpoint, retries - 1, callback);
      }
    }, 1000);
  });
}

let EndpointsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [EndpointsActions],

  init: function() {
    this.setInData(['deleteConfirmationLoading'], false);
  },

  onRetrieveEndpoints: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadEndpoints()).then((result) => {
        var endpoints = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            endpoints.push(result[key]);
          }
        }

        this.setInData(['items'], endpoints);
        this.setInData(['itemsLoading'], false);
        this.emitChange();
      });
    }
  },

  onEditEndpoint: function(endpoint) {
    this.setInData(['editingItemData', 'item'], endpoint);
    this.emitChange();
  },

  onCancelEditEndpoint: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateEndpoint: function(endpoint) {

    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.createEndpoint(endpoint).then((createdEndpoint) => {
      verifyEnumeration(createdEndpoint, ENUMERATION_RETRIES, () => {
        var immutableEndpoint = Immutable(createdEndpoint);

        var endpoints = this.data.items.asMutable();
        endpoints.push(immutableEndpoint);

        this.setInData(['items'], endpoints);
        this.setInData(['newItem'], immutableEndpoint);
        this.setInData(['editingItemData'], null);
        this.emitChange();

        setTimeout(() => {
          this.setInData(['newItem'], null);
          this.emitChange();
        }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
      });
    }).catch(this.onGenericEditError);
  },

  onUpdateEndpoint: function(endpoint) {

    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.updateEndpoint(endpoint).then((updatedEndpoint) => {
      // If the backend did not make any changes, the response will be empty
      updatedEndpoint = updatedEndpoint || endpoint;

      verifyEnumeration(updatedEndpoint, ENUMERATION_RETRIES, () => {
        var immutableEndpoint = Immutable(updatedEndpoint);
        var endpoints = this.data.items.asMutable();

        for (var i = 0; i < endpoints.length; i++) {
          if (endpoints[i].documentSelfLink === immutableEndpoint.documentSelfLink) {
            endpoints[i] = immutableEndpoint;
          }
        }

        this.setInData(['items'], endpoints);
        this.setInData(['updatedItem'], immutableEndpoint);
        this.setInData(['editingItemData'], null);
        this.emitChange();

        setTimeout(() => {
          this.setInData(['updatedItem'], null);
          this.emitChange();
        }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
      });
    }).catch(this.onGenericEditError);
  },

  onDeleteEndpoint: function(endpoint) {
    this.setInData(['deleteConfirmationLoading'], true);
    this.emitChange();

    services.deleteEndpoint(endpoint).then(() => {
      var endpoints = this.data.items.filter((item) =>
          item.documentSelfLink !== endpoint.documentSelfLink);
      this.setInData(['items'], endpoints);
      this.setInData(['deleteConfirmationLoading'], false);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], endpoint);
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

export default EndpointsStore;
