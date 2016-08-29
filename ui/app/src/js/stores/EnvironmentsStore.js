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

import { EnvironmentsActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let EnvironmentsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [EnvironmentsActions],

  onOpenEnvironments: function() {
    this.setInData(['editingItemData'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadEnvironments()).then((result) => {
        var environments = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            environments.push(result[key]);
          }
        }

        this.setInData(['items'], environments);
        this.setInData(['itemsLoading'], false);
        this.emitChange();
      });
    }
  },

  onEditEnvironment: function(environment) {
    this.setInData(['editingItemData', 'item'], environment);
    if (Object.keys(environment.properties || {}).length === 0) {
      this.setInData(['editingItemData', 'property'], {});
    }
    this.emitChange();
  },

  onCancelEditEnvironment: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateEnvironment: function(environment) {
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.createEnvironment(environment).then((createdEnvironment) => {
      var immutableEnvironment = Immutable(createdEnvironment);

      var environments = this.data.items.asMutable();
      environments.push(immutableEnvironment);

      this.setInData(['items'], environments);
      this.setInData(['newItem'], immutableEnvironment);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['newItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

    }).catch(this.onGenericEditError);
  },

  onUpdateEnvironment: function(environment) {
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    services.updateEnvironment(environment).then((updatedEnvironment) => {
      // If the backend did not make any changes, the response will be empty
      updatedEnvironment = updatedEnvironment || environment;

      var immutableEnvironment = Immutable(updatedEnvironment);

      var environments = this.data.items.asMutable();

      for (var i = 0; i < environments.length; i++) {
        if (environments[i].documentSelfLink === immutableEnvironment.documentSelfLink) {
          environments[i] = immutableEnvironment;
        }
      }

      this.setInData(['items'], environments);
      this.setInData(['updatedItem'], immutableEnvironment);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  },

  onDeleteEnvironment: function(environment) {
    services.deleteEnvironment(environment).then(() => {
      var environments = this.data.items.asMutable();

      for (var i = environments.length - 1; i >= 0; i--) {
        if (environments[i].documentSelfLink === environment.documentSelfLink) {
          environments.splice(i, 1);
        }
      }

      this.setInData(['items'], environments);
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

