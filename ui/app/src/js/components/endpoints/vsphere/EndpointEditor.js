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

import services from 'core/services';

export default Vue.component('vsphere-endpoint-editor', {
  template: `
    <div>
      <text-group
        :disabled="!!model.documentSelfLink || !!this.verified"
        :label="i18n('app.endpoint.edit.vsphere.hostNameLabel')"
        :required="true"
        :value="hostName"
        @change="onHostNameChange">
      </text-group>
      <text-group
        :disabled="!!this.verified"
        :label="i18n('app.endpoint.edit.vsphere.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-group>
      <password-group
        :disabled="!!this.verified"
        :label="i18n('app.endpoint.edit.vsphere.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-group>
      <dropdown-search-group
        v-if="verified"
        :entity="i18n('app.endpoint.datacenterEntity')"
        :label="i18n('app.endpoint.edit.vsphere.regionIdLabel')"
        :loading="!regionIdValues"
        :options="regionIdValues"
        :required="true"
        :value="convertToObject(regionId)"
        @change="onRegionIdChange">
      </dropdown-search-group>
      <dropdown-search-group
        v-if="verified"
        :entity="i18n('app.endpoint.edit.vsphere.linkedEndpointLabel')"
        :label="i18n('app.endpoint.edit.vsphere.linkedEndpointLabel')"
        :filter="searchLinkedEndpoints"
        :value="linkedEndpoint"
        @change="onLinkedEndpointChange">
      </dropdown-search-group>
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
      hostName: properties.hostName,
      linkedEndpoint: this.model.linkedEndpoint,
      privateKeyId: properties.privateKeyId,
      privateKey: properties.privateKey,
      regionId: properties.regionId,
      regionIdValues: null
    };
  },
  attached: function() {
    this.unwatchVerified = this.$watch('verified', (verified) => {
      if (verified) {
        this.searchRegionIds();
      }
      this.emitChange();
    });
    this.emitChange();
  },
  detached: function() {
     this.unwatchVerified();
  },
  methods: {
    onHostNameChange(hostName) {
      this.hostName = hostName;
      this.emitChange();
    },
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.emitChange();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.emitChange();
    },
    onRegionIdChange(regionIdObject) {
      this.regionId = regionIdObject && regionIdObject.id;
      this.emitChange();
    },
    onLinkedEndpointChange(endpoint) {
      this.linkedEndpoint = endpoint;
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          hostName: this.hostName,
          linkedEndpointLink: this.linkedEndpoint && this.linkedEndpoint.documentSelfLink,
          privateKeyId: this.privateKeyId,
          privateKey: this.privateKey,
          regionId: this.regionId
        },
        valid: this.hostName && this.privateKeyId && this.privateKey &&
            (!this.verified || this.regionId)
      });
    },
    searchRegionIds() {
      let {hostName, privateKeyId, privateKey} = this;
      let request = {
        host: hostName,
        username: privateKeyId,
        password: privateKey
      };
      api.client.patch('/provisioning/vsphere/dc-enumerator', request).then((result) => {
        this.regionIdValues = result.datacenters.map(this.convertToObject);
      });
    },
    searchLinkedEndpoints(...args) {
      return new Promise((resolve, reject) => {
        services.searchEndpoints.apply(null, [...args, 'nsxt']).then((result) => {
          resolve(result);
        }).catch(reject);
      });
    },
    convertToObject(value) {
      if (value) {
        return {
          id: value,
          name: value
        };
      }
    }
  }
});
