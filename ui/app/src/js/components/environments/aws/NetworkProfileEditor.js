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
        :label="i18n('app.environment.edit.nameLabel')"
        :value="name"
        @change="onNameChange">
      </text-group>
      <multicolumn-editor-group
        v-if="endpoint"
        :headers="[
          i18n('app.environment.edit.nameLabel')
        ]"
        :label="i18n('app.environment.edit.subnetworksLabel')"
        :value="subnetworks"
        @change="onSubnetworkChange">
        <multicolumn-cell name="name">
          <dropdown-search
            :entity="i18n('app.network.entity')"
            :filter="searchSubnetworks"
            :renderer="renderSubnetwork"
            :manage="[{
              action: manageSubnetworks,
              icon: 'pencil',
              name: i18n('app.subnetwork.manage')
            }]">
          </dropdown-search>
        </multicolumn-cell>
      </multicolumn-editor-group>
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
    renderSubnetwork(network) {
      let props = [
        i18n.t('app.environment.edit.cidrLabel') + ':' + network.subnetCIDR
      ];
      if (network.supportPublicIpAddress) {
        props.push(i18n.t('app.environment.edit.supportPublicIpAddressLabel'));
      }
      if (network.defaultForZone) {
        props.push(i18n.t('app.environment.edit.defaultForZoneLabel'));
      }
      let secondary = props.join(', ');
      return `
        <div>
          <div class="host-picker-item-primary" title="${network.name}">${network.name}</div>
          <div class="host-picker-item-secondary" title="${secondary}">
            ${secondary}
          </div>
        </div>`;
    },
    searchSubnetworks(...args) {
      return new Promise((resolve, reject) => {
        services.searchSubnetworks.apply(null,
            [this.endpoint.documentSelfLink, ...args]).then((result) => {
          resolve(result);
        }).catch(reject);
      });
    },
    manageSubnetworks() {
      this.$emit('manage.subnetworks');
    },
    emitChange() {
      this.$emit('change', {
        properties: {
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
