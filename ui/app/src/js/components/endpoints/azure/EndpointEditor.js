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
      <text-input
        :label="i18n('app.endpoint.edit.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-input>
      <password-input
        :label="i18n('app.endpoint.edit.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-input>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.azure.userLinkLabel')"
        :required="true"
        :value="userLink"
        @change="onUserLinkChange">
      </text-input>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.azure.tenantIdLabel')"
        :required="true"
        :value="azureTenantId"
        @change="onAzureTenantIdChange">
      </text-input>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.regionIdLabel')"
        :required="true"
        :value="regionId"
        @change="onRegionIdChange">
      </text-input>
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
      userLink: properties.userLink,
      azureTenantId: properties.azureTenantId,
      regionId: properties.regionId
    };
  },
  methods: {
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.dispatchChangeIfNeeded();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.dispatchChangeIfNeeded();
    },
    onUserLinkChange(userLink) {
      this.userLink = userLink;
      this.dispatchChangeIfNeeded();
    },
    onAzureTenantIdChange(azureTenantId) {
      this.azureTenantId = azureTenantId;
      this.dispatchChangeIfNeeded();
    },
    onRegionIdChange(regionId) {
      this.regionId = regionId;
      this.dispatchChangeIfNeeded();
    },
    dispatchChangeIfNeeded() {
      if (this.privateKeyId && this.privateKey && this.userLink &&
          this.azureTenantId && this.regionId) {
        this.$dispatch('change', {
          privateKeyId: this.privateKeyId,
          privateKey: this.privateKey,
          userLink: this.userLink,
          azureTenantId: this.azureTenantId,
          regionId: this.regionId
        });
      }
    }
  }
});
