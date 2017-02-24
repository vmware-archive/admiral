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

export default Vue.component('aws-endpoint-editor', {
  template: `
    <div>
      <text-group
        :label="i18n('app.endpoint.edit.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-group>
      <password-group
        :label="i18n('app.endpoint.edit.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-group>
      <text-group
        :disabled="!!model.documentSelfLink"
        :label="i18n('app.endpoint.edit.regionIdLabel')"
        :required="true"
        :value="regionId"
        @change="onRegionIdChange">
      </text-group>
    </div>
  `,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let properties = this.model.endpointProperties || {};
    return {
      privateKeyId: properties.privateKeyId,
      privateKey: properties.privateKey,
      regionId: properties.regionId
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.emitChange();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.emitChange();
    },
    onRegionIdChange(regionId) {
      this.regionId = regionId;
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          privateKeyId: this.privateKeyId,
          privateKey: this.privateKey,
          regionId: this.regionId
        },
        valid: this.privateKeyId && this.privateKey && this.regionId
      });
    }
  }
});
