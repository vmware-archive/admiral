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

import services from 'core/services';

export default Vue.component('aws-network-profile-editor', {
  template: `
    <div>
      <text-group
        :label="i18n('app.profile.edit.nameLabel')"
        :value="name"
        @change="onNameChange">
      </text-group>
      <multicolumn-editor-group
        v-if="endpoint"
        :headers="[
          i18n('app.profile.edit.nameLabel')
        ]"
        :label="i18n('app.profile.edit.subnetworksLabel')"
        :value="subnetworks"
        @change="onSubnetworkChange">
        <multicolumn-cell name="name">
          <subnetwork-search
            :endpoint="endpoint"
            :manage-action="manageSubnetworks">
          </subnetwork-search>
        </multicolumn-cell>
      </multicolumn-editor-group>
      <dropdown-group
        v-if="endpoint"
        :entity="i18n('app.profile.edit.isolationNetworkLabel')"
        :label="i18n('app.profile.edit.isolationTypeLabel')"
        :options="isolationTypes"
        :value="convertToObject(model.isolationType)"
        @change="onIsolationTypeChange">
      </dropdown-group>
      <dropdown-search-group
        v-if="isolationType && isolationType.id === 'SUBNET'"
        :entity="i18n('app.network.entity')"
        :filter="searchIsolationNetworks"
        :label="i18n('app.profile.edit.isolationNetworkLabel')"
        :value="model.isolationNetwork"
        @change="onIsolationNetworkChange">
      </dropdown-search-group>
    </div>
  `,
  props: {
    endpoint: {
      required: false,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let subnetworks = this.model.subnetworks &&
        this.model.subnetworks.asMutable() || [];
    return {
      isolationNetwork: this.model.isolationNetwork,
      isolationType: this.model.isolationType,
      isolationTypes: [{
        id: 'NONE',
        name: i18n.t('app.profile.edit.noneIsolationTypeLabel')
      }, {
        id: 'SUBNET',
        name: i18n.t('app.profile.edit.subnetIsolationTypeLabel')
      }, {
        id: 'SECURITY_GROUP',
        name: i18n.t('app.profile.edit.securityGroupIsolationTypeLabel')
      }],
      name: this.model.name,
      subnetworks: subnetworks.map((subnetwork) => {
        return {
          name: subnetwork
        };
      })
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onNameChange(value) {
      this.name = value;
      this.emitChange();
    },
    onSubnetworkChange(value) {
      this.subnetworks = value;
      this.emitChange();
    },
    onIsolationTypeChange(value) {
      this.isolationType = value;
      this.isolationNetwork = null;
      this.emitChange();
    },
    onIsolationNetworkChange(value) {
      this.isolationNetwork = value;
      this.emitChange();
    },
    searchIsolationNetworks(...args) {
      return new Promise((resolve, reject) => {
        services.searchNetworks.apply(null,
            [this.endpointLink, ...args]).then((result) => {
          resolve(result);
        }).catch(reject);
      });
    },
    convertToObject(value) {
      if (value) {
        return this.isolationTypes.find((type) => type.id === value);
      }
    },
    manageSubnetworks() {
      this.$emit('manage.subnetworks');
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          isolationType: this.isolationType && this.isolationType.id,
          isolationNetworkLink: this.isolationNetwork &&
              this.isolationNetwork.documentSelfLink,
          name: this.name,
          subnetLinks: this.subnetworks.reduce((previous, current) => {
            if (current.name && current.name.documentSelfLink) {
              previous.push(current.name.documentSelfLink);
            }
            return previous;
          }, [])
        },
        valid: true
      });
    }
  }
});
