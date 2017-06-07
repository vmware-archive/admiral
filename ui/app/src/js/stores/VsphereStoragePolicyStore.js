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

import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import { VsphereStoragePolicyActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';

const OPERATION = {
  LIST: 'list'
};

export default Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [VsphereStoragePolicyActions],

  onRetrieveStoragePolicies: function(endpointLink, nameFilter) {
   let operation = this.requestCancellableOperation(OPERATION.LIST);
   if (operation) {
     this.setInData(['itemsLoading'], true);
     this.emitChange();

     operation.forPromise(services.loadVsphereStoragePolicies(endpointLink, nameFilter))
       .then((response) => {
         let storagePolicies = utils.getDocumentArray(response);
         let tagsPromises = storagePolicies.map((storagePolicy) => {
           if (storagePolicy.tagLinks && storagePolicy.tagLinks.length) {
             return services.loadTags(storagePolicy.tagLinks).then((tags) => {
               storagePolicy.tags = Object.values(tags);
               return storagePolicy;
             });
           } else {
             storagePolicy.tags = [];
             return Promise.resolve(storagePolicy);
           }
         });
         Promise.all(tagsPromises).then(() => {
           this.setInData(['items'], storagePolicies);
           this.setInData(['itemsLoading'], false);
           this.emitChange();
         });
       }
     );
   }
 },

  onEditStoragePolicy: function(storagePolicy) {
    this.setInData(['editingItemData', 'item'], storagePolicy);
    this.emitChange();
  },

  onCancelEditStoragePolicy: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onUpdateStoragePolicy: function(storagePolicy, tagRequest) {

    this.setInData(['editingItemData', 'item'], storagePolicy);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all([
      services.updateVsphereStoragePolicy(storagePolicy),
      services.updateTagAssignment(tagRequest)]).then(([updatedStoragePolicy]) => {
      // If the backend did not make any changes, the response will be empty
      updatedStoragePolicy = updatedStoragePolicy || storagePolicy;

      var immutableStoragePolicy = Immutable(updatedStoragePolicy);
      var storagePolicies = this.data.items.asMutable();

      for (var i = 0; i < storagePolicies.length; i++) {
        if (storagePolicies[i].documentSelfLink === immutableStoragePolicy.documentSelfLink) {
          storagePolicies[i] = immutableStoragePolicy;
        }
      }

      this.setInData(['items'], storagePolicies);
      this.setInData(['updatedItem'], immutableStoragePolicy);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  }
});
