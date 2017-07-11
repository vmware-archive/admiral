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

import VsphereDatastoreEditorVue
  from 'components/profiles/vsphere/VsphereDatastoreEditorVue.html';
import utils from 'core/utils';
import { VsphereDatastoreActions } from 'actions/Actions';

export default Vue.component('vsphere-datastore-editor', {
  template: VsphereDatastoreEditorVue,
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
      capacity: utils.convertToGigabytes(this.model.item.capacityBytes),
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
      VsphereDatastoreActions.cancelEditDatastore();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      let toSave = this.getModel();
      var tagRequest = utils.createTagAssignmentRequest(toSave.documentSelfLink,
        this.model.item.tags || [], this.tags);
      VsphereDatastoreActions.updateDatastore(toSave, tagRequest);
      VsphereDatastoreActions.retrieveDatastores(this.model.item.endpointLink);
    },
    getModel() {
      return $.extend({}, this.model.item, {
        tagLinks: undefined,
        supportsEncryption: this.supportsEncryption
      });
    }
  }
});
