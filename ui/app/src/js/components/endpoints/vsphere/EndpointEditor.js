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
import utils from 'core/utils';

export default Vue.component('vsphere-endpoint-editor', {
  template: `
    <div>
      <text-group
        :disabled="!!model.documentSelfLink"
        :label="i18n('app.endpoint.edit.vsphere.hostNameLabel')"
        :required="true"
        :value="hostName"
        @change="onHostNameChange">
      </text-group>
      <text-group
        :label="i18n('app.endpoint.edit.vsphere.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-group>
      <password-group
        :label="i18n('app.endpoint.edit.vsphere.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-group>
      <dropdown-search-group
        :disabled="!!model.documentSelfLink || !(regionIdLoading || regionIdValues.length)"
        :entity="i18n('app.endpoint.datacenterEntity')"
        :label="i18n('app.endpoint.edit.vsphere.regionIdLabel')"
        :loading="regionIdLoading"
        :options="regionIdValues"
        :required="true"
        :value="convertToObject(regionId)"
        @change="onRegionIdChange">
      </dropdown-search-group>
      <dropdown-search-group
        :disabled="!!model.documentSelfLink && !!model.linkedEndpoint"
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
      regionIdValues: [],
      regionIdLoading: false
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onHostNameChange(hostName) {
      this.hostName = hostName;
      this.regionId = null;
      this.regionIdValues = [];
      this.emitChange();
    },
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.regionId = null;
      this.regionIdValues = [];
      this.emitChange();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.regionId = null;
      this.regionIdValues = [];
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
      if (this.hostName && this.privateKeyId && this.privateKey) {
        if (!this.regionIdValues.length) {
          this.searchRegionIds();
        }
      } else {
        if (!(this.model.documentSelfLink && !this.privateKey)) {
          this.regionId = null;
        }
        this.regionIdValues = [];
      }
      this.$emit('change', {
        properties: {
          hostName: this.hostName,
          linkedEndpointLink: this.linkedEndpoint && this.linkedEndpoint.documentSelfLink,
          privateKeyId: this.privateKeyId,
          privateKey: this.privateKey,
          regionId: this.regionId
        },
        valid: this.hostName && this.privateKeyId && this.privateKey && this.regionId
      });
    },
    searchRegionIds() {
      let {hostName, privateKeyId, privateKey} = this;
      if (!this.regionIdLoading) {
        this.regionIdLoading = true;
        services.searchRegionIds(hostName, privateKeyId, privateKey).then((result) => {
          this.regionIdLoading = false;
          this.regionIdValues = result.datacenters.map(this.convertToObject);
        }, (e) => {
          this.regionIdLoading = false;
          if (hostName !== this.hostName || privateKeyId !== this.privateKeyId ||
              privateKey !== this.privateKey) {
            this.searchRegionIds();
          } else {
            this.$emit('error', utils.getValidationErrors(e));
          }
        });
      }
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
