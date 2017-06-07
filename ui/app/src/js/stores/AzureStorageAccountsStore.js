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

import { AzureStorageAccountsActions } from 'actions/Actions';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';

const OPERATION = {
  LIST: 'list'
};

export default Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [AzureStorageAccountsActions],

  onRetrieveAccounts: function(nameFilter) {
    let operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadAzureStorageAccounts(nameFilter)).then((response) => {
        let accounts = utils.getDocumentArray(response);
        let tagsPromises = accounts.map((account) => {
          if (account.tagLinks && account.tagLinks.length) {
            return services.loadTags(account.tagLinks).then((tags) => {
              account.tags = Object.values(tags);
              return account;
            });
          } else {
            account.tags = [];
            return Promise.resolve(account);
          }
        });
        Promise.all(tagsPromises).then(() => {
          this.setInData(['items'], accounts);
          this.setInData(['itemsLoading'], false);
          this.emitChange();
        });
      });
    }
  },

  onEditAccount: function(storageAccount) {
    this.setInData(['editingItemData', 'item'], storageAccount);
    this.emitChange();
  },

  onCancelEditAccount: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onUpdateAccount: function(storageAccount, tagRequest) {

    this.setInData(['editingItemData', 'item'], storageAccount);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();

    Promise.all([
      services.updateStorageAccount(storageAccount),
      services.updateTagAssignment(tagRequest)]).then(([updatedStorageAccount]) => {
      // If the backend did not make any changes, the response will be empty
      updatedStorageAccount = updatedStorageAccount || storageAccount;

      var immutableStorageAccount = Immutable(updatedStorageAccount);
      var storageAccounts = this.data.items.asMutable();

      for (var i = 0; i < storageAccounts.length; i++) {
        if (storageAccounts[i].documentSelfLink === immutableStorageAccount.documentSelfLink) {
          storageAccounts[i] = immutableStorageAccount;
        }
      }

      this.setInData(['items'], storageAccounts);
      this.setInData(['updatedItem'], immutableStorageAccount);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      setTimeout(() => {
        this.setInData(['updatedItem'], null);
        this.emitChange();
      }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
    }).catch(this.onGenericEditError);
  }
});
