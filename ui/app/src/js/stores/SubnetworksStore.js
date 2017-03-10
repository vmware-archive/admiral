/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { SubnetworksActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

export default Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [SubnetworksActions],

  init: function() {
    this.setInData(['deleteConfirmationLoading'], false);
  },

  onRetrieveSubnetworks: function(endpointLink) {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadSubnetworks(endpointLink)).then((result) => {
        var subnetworks = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            subnetworks.push(result[key]);
          }
        }

        var networkLinks = subnetworks.map((subnetwork) => subnetwork.networkLink);
        var tagsPromises = subnetworks.map((subnetwork) => {
          if (subnetwork.tagLinks && subnetwork.tagLinks.length) {
            return services.loadTags(subnetwork.tagLinks).then((tags) => {
              subnetwork.tags = Object.values(tags);
              return subnetwork;
            });
          } else {
            subnetwork.tags = [];
            return Promise.resolve(subnetwork);
          }
        });

        services.loadNetworks(endpointLink, networkLinks).then((networks) => {
          subnetworks.forEach((subnetwork) => {
            subnetwork.network = networks[subnetwork.networkLink];
          });
          return Promise.all(tagsPromises);
        }).then(() => {
          this.setInData(['endpointLink'], endpointLink);
          this.setInData(['items'], subnetworks);
          this.setInData(['itemsLoading'], false);
          this.emitChange();
        });
      });
    }
  },

  onEditSubnetwork: function(subnetwork) {
    this.setInData(['editingItemData', 'item'], subnetwork);
    this.emitChange();
  },

  onCancelEditSubnetwork: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateSubnetwork: function(subnetwork, tagRequest) {

    this.setInData(['editingItemData', 'item'], subnetwork);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    let immutableSubnetwork;

    services.createSubnetwork(subnetwork).then((createdSubnetwork) => {
      immutableSubnetwork = Immutable(createdSubnetwork);
      if (tagRequest) {
        tagRequest.resourceLink = createdSubnetwork.documentSelfLink;
        return services.updateTagAssignment(tagRequest);
      }
      return Promise.resolve();
    }).then(() => {
      var subnetworks = this.data.items.asMutable();
      subnetworks.push(immutableSubnetwork);

      this.setInData(['items'], subnetworks);
      this.setInData(['newItem'], immutableSubnetwork);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['newItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  },

  onUpdateSubnetwork: function(subnetwork, tagRequest) {

    this.setInData(['editingItemData', 'item'], subnetwork);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all([
        services.updateSubnetwork(subnetwork),
        services.updateTagAssignment(tagRequest)]).then(([updatedSubnetwork]) => {
      // If the backend did not make any changes, the response will be empty
      updatedSubnetwork = updatedSubnetwork || subnetwork;

      var immutableSubnetwork = Immutable(updatedSubnetwork);
      var subnetworks = this.data.items.asMutable();

      for (var i = 0; i < subnetworks.length; i++) {
        if (subnetworks[i].documentSelfLink === immutableSubnetwork.documentSelfLink) {
          subnetworks[i] = immutableSubnetwork;
        }
      }

      this.setInData(['items'], subnetworks);
      this.setInData(['updatedItem'], immutableSubnetwork);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  },

  onDeleteSubnetwork: function(subnetwork) {
    this.setInData(['deleteConfirmationLoading'], true);
    this.emitChange();

    services.deleteSubnetwork(subnetwork).then(() => {
      var subnetworks = this.data.items.filter((item) =>
          item.documentSelfLink !== subnetwork.documentSelfLink);
      this.setInData(['items'], subnetworks);
      this.setInData(['deleteConfirmationLoading'], false);
      this.emitChange();
    }).catch((e) => {
      var validationErrors = utils.getValidationErrors(e);
      this.setInData(['updatedItem'], subnetwork);
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
