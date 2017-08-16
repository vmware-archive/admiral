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

import VsphereStoragePolicyEditorVue
  from 'components/profiles/vsphere/VsphereStoragePolicyEditorVue.html';
import utils from 'core/utils';
import { VsphereStoragePolicyActions } from 'actions/Actions';

export default Vue.component('vsphere-storage-policy-editor', {
  template: VsphereStoragePolicyEditorVue,
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
      description: this.model.item.desc,
      supportsEncryption: this.model.item.customProperties.__supportsEncryption === 'true'
    };
  },
  computed: {
    customProperties: function() {
      let customPropertiesMap = this.model.item.customProperties;
      let customProperties = [];
      for (var key in customPropertiesMap) {
        if (customPropertiesMap.hasOwnProperty(key) && key.indexOf('__') === -1) {
          let customProperty = {
            key: key,
            value: customPropertiesMap[key]
          };
          customProperties.push(customProperty);
        }
      }
      return customProperties;
    }
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
      VsphereStoragePolicyActions.cancelEditStoragePolicy();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      let toSave = this.getModel();
      var tagRequest = utils.createTagAssignmentRequest(toSave.documentSelfLink,
        this.model.item.tags || [], this.tags);
      VsphereStoragePolicyActions.updateStoragePolicy(toSave, tagRequest);
      VsphereStoragePolicyActions
        .retrieveStoragePolicies(this.model.item.customProperties.__endpointLink);
    },
    getModel() {
      return $.extend(true, {}, this.model.item, {
        tagLinks: undefined,
        customProperties: {
          __supportsEncryption: this.supportsEncryption.toString()
        }
      });
    }
  }
});
