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
import VueMulticolumnInputs from 'components/common/VueMulticolumnInputs'; //eslint-disable-line
import VueTags from 'components/common/VueTags'; //eslint-disable-line
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import EnvironmentEditViewVue from 'components/environments/EnvironmentEditViewVue.html';

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
  data: () => {
    return {
      endpointType: null,
      saveDisabled: true,
      tags: this.model.item.tags || []
    };
  },
  computed: {
    validationErrors() {
      return this.model.validationErrors || {};
    },
    activeContextItem() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    instanceTypeValue() {
      if (this.model.item.computeProfile) {
        var mappings = this.model.item.computeProfile.instanceTypeMapping;
        return Object.keys(mappings).map((key) => {
          if (this.endpointType === 'vsphere') {
            return {
              name: key,
              cpuCount: mappings[key].cpuCount,
              diskSizeMb: mappings[key].diskSizeMb,
              memoryMb: mappings[key].memoryMb
            };
          } else {
            return {
              name: key,
              value: mappings[key].instanceType
            };
          }
        });
      }
      return {};
    },
    imageTypeValue() {
      if (this.model.item.computeProfile) {
        var mappings = this.model.item.computeProfile.imageMapping;
        return Object.keys(mappings).map((key) => {
          return {
            name: key,
            value: mappings[key].image
          };
        });
      }
      return {};
    },
    bootDiskPropertyValue() {
      if (this.model.item.storageProfile) {
        var mappings = this.model.item.storageProfile.bootDiskPropertyMapping;
        return Object.keys(mappings).map((key) => {
          if (this.endpointType === 'azure') {
            return {
              name: key,
              value: mappings[key]
            };
          }
        });
      }
      return {};
    }
  },
  attached() {
    $(this.$el).find('.nav a[data-toggle=pill]').on('click', (e) => {
      if ($(e.target).parent().hasClass('disabled')) {
        e.preventDefault();
        return false;
      }
    });

    $(this.$el).find('.nav-item a[href="#basic"]').tab('show');

    this.unwatchModel = this.$watch('model', (model, oldModel) => {
        oldModel = oldModel || { item: {} };
        this.endpointType =
            (this.model.item.endpoint && this.model.item.endpoint.endpointType) ||
            this.model.item.endpointType;
        this.saveDisabled = !this.model.item.name ||
            !(this.model.item.endpointType || this.model.item.endpoint);
    }, {immediate: true});
  },
  detached() {
    this.unwatchModel();
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
    closeToolbar() {
      EnvironmentsActions.closeToolbar();
    },
    onNameChange() {
      Vue.nextTick(() => {
        var model = this.getModel();
        this.saveDisabled = !model.name || !(model.endpointType || this.endpoint);
      });
    },
    onEndpointChange(endpoint) {
      this.endpoint = endpoint;
      Vue.nextTick(() => {
        var model = this.getModel();
        this.endpointType = this.endpoint && this.endpoint.endpointType;
        this.saveDisabled = !model.name || !(model.endpointType || this.endpoint);
      });
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    getModel() {
      var toSave = $.extend({ properties: {} }, this.model.item.asMutable({deep: true}));

      toSave.name = $(this.$el).find('.name input').val();
      toSave.endpointLink = this.endpoint && this.endpoint.documentSelfLink;
      toSave.computeProfile = toSave.computeProfile || {};
      toSave.storageProfile = toSave.storageProfile || {};
      toSave.networkProfile = toSave.networkProfile || {};

      if (this.$refs.instanceType) {
        var instanceType = this.$refs.instanceType.getData();
        toSave.computeProfile.instanceTypeMapping = instanceType.reduce((previous, current) => {
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
      }

      if (this.$refs.imageType) {
        var imageType = this.$refs.imageType.getData();
        toSave.computeProfile.imageMapping = imageType.reduce((previous, current) => {
          previous[current.name] = {
            image: current.value
          };
          return previous;
        }, {});
      }

      return toSave;
    }
  }
});
