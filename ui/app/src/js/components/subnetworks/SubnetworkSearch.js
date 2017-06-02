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
import utils from 'core/utils';

export default Vue.component('subnetwork-search', {
  template: `
    <div>
      <dropdown-search
        :disabled="disabled"
        :entity="i18n('app.subnetwork.entity')"
        :filter="searchSubnetworks"
        :renderer="renderSubnetwork"
        :manage="manage"
        :value="value"
        :value-renderer="renderSubnetworkValue"
        @change="onChange">
      </dropdown-search>
    </div>
  `,
  props: {
    createAction: {
      required: false,
      type: Function
    },
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    endpoint: {
      required: false,
      type: Object
    },
    manageAction: {
      required: false,
      type: Function
    },
    value: {
      required: false,
      type: Object
    }
  },
  data() {
    let manage = [];
    if (this.createAction) {
      manage.push({
        action: this.createAction,
        icon: 'plus',
        name: i18n.t('app.subnetwork.create')
      });
    }
    if (this.manageAction) {
      manage.push({
        action: this.manageAction,
        icon: 'pencil',
        name: i18n.t('app.subnetwork.manage')
      });
    }
    return {
      manage
    };
  },
  methods: {
    renderSubnetwork(subnetwork) {
      let props = [
        i18n.t('app.profile.edit.cidrLabel') + ': ' + utils.escapeHtml(subnetwork.subnetCIDR),
        i18n.t('app.profile.edit.networkLabel') + ': ' + utils.escapeHtml(subnetwork.networkName)
      ];
      if (subnetwork.supportPublicIpAddress) {
        props.push(i18n.t('app.profile.edit.supportPublicIpAddressLabel'));
      }
      if (subnetwork.defaultForZone) {
        props.push(i18n.t('app.profile.edit.defaultForZoneLabel'));
      }
      let secondary = props.join(', ');
      return `
        <div>
          <div class="host-picker-item-primary" title="${subnetwork.name}">
            ${utils.escapeHtml(subnetwork.name)}
          </div>
          <div class="host-picker-item-secondary" title="${secondary}">
            ${secondary}
          </div>
        </div>`;
    },
    renderSubnetworkValue(subnetwork) {
      let props = [
        i18n.t('app.profile.edit.cidrLabel') + ': ' + utils.escapeHtml(subnetwork.subnetCIDR),
        i18n.t('app.profile.edit.networkLabel') + ': ' + utils.escapeHtml(subnetwork.networkName)
      ];
      if (subnetwork.supportPublicIpAddress) {
        props.push(i18n.t('app.profile.edit.supportPublicIpAddressLabel'));
      }
      if (subnetwork.defaultForZone) {
        props.push(i18n.t('app.profile.edit.defaultForZoneLabel'));
      }
      let secondary = props.join(', ');
      return `${utils.escapeHtml(subnetwork.name)} <span>(${secondary})</span>`;
    },
    searchSubnetworks(...args) {
      return new Promise((resolve, reject) => {
        services.searchSubnetworks.apply(null,
            [this.endpoint.documentSelfLink, ...args]).then((result) => {
          let networkLinks = [...new Set(result.items.map((item) => item.networkLink))];
          services.loadNetworks(this.endpoint.documentSelfLink, networkLinks).then((networks) => {
            result.items.forEach((item) => {
              item.networkName = networks[item.networkLink].name;
            });
            resolve(result);
          });
        }).catch(reject);
      });
    },
    onChange(value) {
      this.value = value;
      this.$emit('change', value);
    }
  }
});
