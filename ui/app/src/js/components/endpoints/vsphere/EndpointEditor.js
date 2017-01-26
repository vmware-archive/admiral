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

import VueDropdownSearchInput from 'components/common/VueDropdownSearchInput'; //eslint-disable-line
import services from 'core/services';
import utils from 'core/utils';

export default Vue.component('vsphere-endpoint-editor', {
  template: `
    <div>
      <text-input
        :disabled="!!model.documentSelfLink"
        :label="i18n('app.endpoint.edit.vsphere.hostNameLabel')"
        :required="true"
        :value="hostName"
        @change="onHostNameChange">
      </text-input>
      <text-input
        :label="i18n('app.endpoint.edit.vsphere.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-input>
      <password-input
        :label="i18n('app.endpoint.edit.vsphere.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-input>
      <dropdown-search-input
        :disabled="!!model.documentSelfLink || !(regionIdLoading || regionIdValues.length)"
        :entity="i18n('app.endpoint.datacenterEntity')"
        :label="i18n('app.endpoint.edit.vsphere.regionIdLabel')"
        :loading="regionIdLoading"
        :options="regionIdValues"
        :required="true"
        :value="convertToObject(regionId)"
        @change="onRegionIdChange">
      </dropdown-search>
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
      privateKeyId: properties.privateKeyId,
      privateKey: properties.privateKey,
      regionId: properties.regionId,
      regionIdValues: [],
      regionIdLoading: false
    };
  },
  methods: {
    onHostNameChange(hostName) {
      this.hostName = hostName;
      this.dispatchChangeIfNeeded();
    },
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.dispatchChangeIfNeeded();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.dispatchChangeIfNeeded();
    },
    onRegionIdChange(regionIdObject) {
      this.regionId = regionIdObject && regionIdObject.id;
      this.dispatchChange();
    },
    dispatchChange() {
      this.$dispatch('change', {
        hostName: this.hostName,
        privateKeyId: this.privateKeyId,
        privateKey: this.privateKey,
        regionId: this.regionId
      });
    },
    dispatchChangeIfNeeded() {
      if (this.hostName && this.privateKeyId && this.privateKey) {
        if (this.regionIdValues.length) {
          this.dispatchChange();
        } else {
          this.searchRegionIds();
        }
      }
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
            this.$dispatch('error', utils.getValidationErrors(e));
          }
        });
      }
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
