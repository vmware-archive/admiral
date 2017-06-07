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

import AzureStorageAccountEditorVue
  from 'components/profiles/azure/AzureStorageAccountEditorVue.html';
import utils from 'core/utils';
import { AzureStorageAccountsActions } from 'actions/Actions';

export default Vue.component('azure-storage-account-editor', {
  template: AzureStorageAccountEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    return {
      tags: this.model.item.tags && this.model.item.tags.asMutable() || [],
      name: this.model.item.name,
      type: this.model.item.type,
      supportsEncryption: this.model.item.supportsEncryption
    };
  },
  methods: {
    onSupportsEncryptionChange(value) {
      this.supportsEncryption = value;
    },
    onTagsChange(value) {
      this.tags = value;
    },
    cancel($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      AzureStorageAccountsActions.cancelEditAccount();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      let toSave = this.getModel();
      var tagRequest = utils.createTagAssignmentRequest(toSave.documentSelfLink,
        this.model.item.tags || [], this.tags);
      AzureStorageAccountsActions.updateAccount(toSave, tagRequest);
    },
    getModel() {
      return $.extend({}, this.model.item, {
        tagLinks: undefined,
        supportsEncryption: this.supportsEncryption
      });
    }
  }
});
