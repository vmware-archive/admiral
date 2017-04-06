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

export default Vue.component('azure-endpoint-editor', {
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
        :label="i18n('app.endpoint.edit.azure.userLinkLabel')"
        :required="true"
        :value="userLink"
        @change="onUserLinkChange">
      </text-group>
      <text-group
        :disabled="!!model.documentSelfLink"
        :label="i18n('app.endpoint.edit.azure.tenantIdLabel')"
        :required="true"
        :value="azureTenantId"
        @change="onAzureTenantIdChange">
      </text-group>
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
    },
    verified: {
      required: true,
      type: Boolean
    }
  },
  data() {
    let properties = this.model.endpointProperties || {};
    return {
      privateKeyId: properties.privateKeyId,
      privateKey: properties.privateKey,
      userLink: properties.userLink,
      azureTenantId: properties.azureTenantId,
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
    onUserLinkChange(userLink) {
      this.userLink = userLink;
      this.emitChange();
    },
    onAzureTenantIdChange(azureTenantId) {
      this.azureTenantId = azureTenantId;
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
          userLink: this.userLink,
          azureTenantId: this.azureTenantId,
          regionId: this.regionId,
          provisioningPermission: true
        },
        valid: this.privateKeyId && this.privateKey && this.userLink &&
          this.azureTenantId && this.regionId
      });
    }
  }
});
