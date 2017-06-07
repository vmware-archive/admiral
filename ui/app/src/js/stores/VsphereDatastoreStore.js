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
import { VsphereDatastoreActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';

const OPERATION = {
  LIST: 'list'
};

export default Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [VsphereDatastoreActions],

  onRetrieveDatastores: function(endpointLink, nameFilter) {
    let operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadVsphereDatastores(endpointLink, nameFilter))
        .then((response) => {
          let datastores = utils.getDocumentArray(response);
          let tagsPromises = datastores.map((datastore) => {
            if (datastore.tagLinks && datastore.tagLinks.length) {
              return services.loadTags(datastore.tagLinks).then((tags) => {
                datastore.tags = Object.values(tags);
                return datastore;
              });
            } else {
              datastore.tags = [];
              return Promise.resolve(datastore);
            }
          });
          Promise.all(tagsPromises).then(() => {
            this.setInData(['items'], datastores);
            this.setInData(['itemsLoading'], false);
            this.emitChange();
          });
        }
      );
    }
  },

  onEditDatastore: function(datastore) {
    this.setInData(['editingItemData', 'item'], datastore);
    this.emitChange();
  },

  onCancelEditDatastore: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onUpdateDatastore: function(datastore, tagRequest) {

    this.setInData(['editingItemData', 'item'], datastore);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all([
      services.updateVsphereDatastore(datastore),
      services.updateTagAssignment(tagRequest)]).then(([updatedDatastore]) => {
      // If the backend did not make any changes, the response will be empty
      updatedDatastore = updatedDatastore || datastore;

      var immutableDatastore = Immutable(updatedDatastore);
      var datastores = this.data.items.asMutable();

      for (var i = 0; i < datastores.length; i++) {
        if (datastores[i].documentSelfLink === immutableDatastore.documentSelfLink) {
          datastores[i] = immutableDatastore;
        }
      }

      this.setInData(['items'], datastores);
      this.setInData(['updatedItem'], immutableDatastore);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  }
});
