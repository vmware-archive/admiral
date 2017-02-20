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

import { EnvironmentsActions, NavigationActions } from 'actions/Actions';
import VueDropdownSearch from 'components/common/VueDropdownSearch'; //eslint-disable-line
import VueTags from 'components/common/VueTags'; //eslint-disable-line
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import SubnetworksList from 'components/subnetworks/SubnetworksList'; //eslint-disable-line
import EnvironmentEditViewVue from 'components/environments/EnvironmentEditViewVue.html';
import services from 'core/services';

export default Vue.component('environment-edit-view', {
  template: EnvironmentEditViewVue,
  props: {
    model: {
      default: () => ({
        contextView: {}
      }),
      required: true,
      type: Object
    }
  },
  data() {
    let endpointType = this.model.item.endpoint && this.model.item.endpoint.endpointType ||
        this.model.item.endpointType;
    let instanceTypeMapping = this.model.item.computeProfile &&
        this.model.item.computeProfile.instanceTypeMapping &&
        this.model.item.computeProfile.instanceTypeMapping.asMutable() || [];
    let imageTypeMapping = this.model.item.computeProfile &&
        this.model.item.computeProfile.imageMapping &&
        this.model.item.computeProfile.imageMapping.asMutable() || [];
    let subnetworks = this.model.item.subnetworks &&
        this.model.item.subnetworks.asMutable() || [];
    let bootDiskPropertyMapping = this.model.item.storageProfile &&
        this.model.item.storageProfile.bootDiskPropertyMapping &&
        this.model.item.storageProfile.bootDiskPropertyMapping.asMutable() || [];
    return {
      bootDiskPropertyValue: Object.keys(bootDiskPropertyMapping).map((key) => {
        return {
          name: key,
          value: bootDiskPropertyMapping[key]
        };
      }),
      currentView: 'basic',
      endpoint: this.model.item.endpoint,
      endpointType,
      imageTypeValue: Object.keys(imageTypeMapping).map((key) => {
        return {
          name: key,
          value: imageTypeMapping[key].image
        };
      }),
      instanceTypeValue: Object.keys(instanceTypeMapping).map((key) => {
        if (endpointType === 'vsphere') {
          return {
            name: key,
            cpuCount: instanceTypeMapping[key].cpuCount,
            diskSizeMb: instanceTypeMapping[key].diskSizeMb,
            memoryMb: instanceTypeMapping[key].memoryMb
          };
        } else {
          return {
            name: key,
            value: instanceTypeMapping[key].instanceType
          };
        }
      }),
      name: this.model.item.name,
      networkName: this.model.item.networkProfile && this.model.item.networkProfile.name,
      subnetworks: subnetworks.map((subnetwork) => {
        return {
          name: subnetwork
        };
      }),
      tags: this.model.item.tags || []
    };
  },
  computed: {
    saveDisabled() {
      return !this.name || !this.endpointType;
    },
    validationErrors() {
      return this.model.validationErrors || {};
    },
    activeContextItem() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded() {
      return this.model.contextView && this.model.contextView.expanded;
    }
  },
  attached() {
    $(this.$el).find('.nav a[data-toggle=pill]').on('click', (e) => {
      if ($(e.target).parent().hasClass('disabled')) {
        e.preventDefault();
        return false;
      }
      this.currentView = $(e.target).attr('href').substring(1);
      EnvironmentsActions.selectView(this.currentView,
          this.endpoint && this.endpoint.documentSelfLink);
    });

    $(this.$el).find('.nav-item a[href="#' + this.currentView + '"]').tab('show');
    EnvironmentsActions.selectView(this.currentView,
        this.endpoint && this.endpoint.documentSelfLink);
  },
  methods: {
    goBack() {
      NavigationActions.openEnvironments();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      let model = this.getModel();
      if (model.documentSelfLink) {
        EnvironmentsActions.updateEnvironment(model, this.tags);
      } else {
        EnvironmentsActions.createEnvironment(model, this.tags);
      }
    },
    createEndpoint() {
      EnvironmentsActions.createEndpoint();
    },
    manageEndpoints() {
      EnvironmentsActions.manageEndpoints();
    },
    manageSubnetworks() {
      EnvironmentsActions.manageSubnetworks();
    },
    closeToolbar() {
      EnvironmentsActions.closeToolbar();
    },
    onNameChange(value) {
      this.name = value;
    },
    onInstanceTypeChange(value) {
      this.instanceTypeValue = value;
    },
    onImageTypeChange(value) {
      this.imageTypeValue = value;
    },
    onEndpointChange(endpoint) {
      this.endpoint = endpoint;
      this.endpointType = endpoint && endpoint.endpointType;
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    onNetworkNameChange(value) {
      this.networkName = value;
    },
    onSubnetworkChange(value) {
      this.subnetworks = value;
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
    onBootDiskPropertyChange(value) {
      this.bootDiskPropertyValue = value;
    },
    getModel() {
      var toSave = $.extend({ properties: {} }, this.model.item.asMutable({deep: true}));
      toSave.name = this.name;
      toSave.endpointLink = this.endpoint && this.endpoint.documentSelfLink;
      toSave.computeProfile = toSave.computeProfile || {};
      toSave.computeProfile.instanceTypeMapping =
          this.instanceTypeValue.reduce((previous, current) => {
            if (this.endpointType === 'vsphere') {
              previous[current.name] = {
                cpuCount: current.cpuCount,
                diskSizeMb: current.diskSizeMb,
                memoryMb: current.memoryMb
              };
            } else {
              previous[current.name] = {
                instanceType: current.value
              };
            }
            return previous;
          }, {});
      toSave.computeProfile.imageMapping = this.imageTypeValue.reduce((previous, current) => {
        previous[current.name] = {
          image: current.value
        };
        return previous;
      }, {});
      toSave.networkProfile = toSave.networkProfile || {};
      toSave.networkProfile.name = this.networkName;
      toSave.networkProfile.subnetLinks = [];
      this.subnetworks.forEach((subnetwork) => {
        if (subnetwork.name && subnetwork.name.documentSelfLink) {
          toSave.networkProfile.subnetLinks.push(subnetwork.name.documentSelfLink);
        }
      });
      toSave.storageProfile = toSave.storageProfile || {};
      toSave.storageProfile.bootDiskPropertyMapping =
          this.bootDiskPropertyValue.reduce((previous, current) => {
            previous[current.name] = current.value;
            return previous;
          }, {});
      return toSave;
    }
  }
});
