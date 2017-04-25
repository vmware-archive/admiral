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

export default Vue.component('azure-network-profile-editor', {
  template: `
    <div>
      <section class="form-block" v-if="endpoint">
        <label>{{i18n('app.profile.edit.existingLabel')}}</label>
        <multicolumn-editor-group
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
      </section>
      <section class="form-block" v-if="endpoint">
        <label>{{i18n('app.profile.edit.isolationLabel')}}</label>
        <dropdown-group
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
          :renderer="renderIsolationNetwork"
          :value="model.isolationNetwork"
          @change="onIsolationNetworkChange">
        </dropdown-search-group>
        <number-group
          v-if="isolationType && isolationType.id === 'SUBNET'"
          :label="i18n('app.profile.edit.cidrPrefixLabel')"
          :value="model.isolatedSubnetCIDRPrefix"
          @change="onIsolatedSubnetCIDRPrefixChange">
        </number-group>
      </section>
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
    onIsolatedSubnetCIDRPrefixChange(value) {
      this.isolatedSubnetCIDRPrefix = value;
      this.emitChange();
    },
    renderIsolationNetwork(network) {
      let secondary = i18n.t('app.profile.edit.resourceGroupsLabel') + ': ' +
          (network.groupNames ? network.groupNames.join(', ') : '');
      return `
        <div>
          <div class="host-picker-item-primary" title="${network.name}">
            ${utils.escapeHtml(network.name)}
          </div>
          <div class="host-picker-item-secondary truncateText" title="${secondary}">
            ${secondary}
          </div>
        </div>`;
    },
    searchIsolationNetworks(...args) {
      return new Promise((resolve, reject) => {
        services.searchNetworks.apply(null,
            [this.endpoint.documentSelfLink, ...args]).then((result) => {
          let groupLinks = result.items.reduce((previous, current) => {
            if (current.groupLinks) {
              previous = previous.concat(current.groupLinks);
            }
            return previous;
          }, []);
          services.loadResourceGroups([...new Set(groupLinks)]).then((groups) => {
            result.items.forEach((item) => {
              if (item.groupLinks) {
                item.groupNames = item.groupLinks.map((groupLink) => {
                  return groups[groupLink].name;
                });
              }
            });
            resolve(result);
          });
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
          isolationNetwork: this.isolationNetwork,
          isolationNetworkLink: this.isolationNetwork &&
              this.isolationNetwork.documentSelfLink,
          isolatedSubnetCIDRPrefix: this.isolatedSubnetCIDRPrefix,
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
