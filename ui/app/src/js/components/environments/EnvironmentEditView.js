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

import AwsComputeProfileEditor from 'components/environments/aws/ComputeProfileEditor'; //eslint-disable-line
import AwsNetworkProfileEditor from 'components/environments/aws/NetworkProfileEditor'; //eslint-disable-line
import AwsStorageProfileEditor from 'components/environments/aws/StorageProfileEditor'; //eslint-disable-line
import AzureComputeProfileEditor from 'components/environments/azure/ComputeProfileEditor'; //eslint-disable-line
import AzureNetworkProfileEditor from 'components/environments/azure/NetworkProfileEditor'; //eslint-disable-line
import AzureStorageProfileEditor from 'components/environments/azure/StorageProfileEditor'; //eslint-disable-line
import vSphereComputeProfileEditor from 'components/environments/vsphere/ComputeProfileEditor'; //eslint-disable-line
import vSphereNetworkProfileEditor from 'components/environments/vsphere/NetworkProfileEditor'; //eslint-disable-line
import vSphereStorageProfileEditor from 'components/environments/vsphere/StorageProfileEditor'; //eslint-disable-line

import { EnvironmentsActions, NavigationActions } from 'actions/Actions';
import VueTags from 'components/common/VueTags'; //eslint-disable-line
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import SubnetworksList from 'components/subnetworks/SubnetworksList'; //eslint-disable-line
import EnvironmentEditViewVue from 'components/environments/EnvironmentEditViewVue.html';

const OOTB_TYPES = [{
  id: 'aws',
  name: 'AWS',
  iconSrc: 'image-assets/endpoints/aws.png'
}, {
  id: 'azure',
  name: 'Azure',
  iconSrc: 'image-assets/endpoints/azure.png'
}, {
  id: 'vsphere',
  name: 'vSphere',
  iconSrc: 'image-assets/endpoints/vsphere.png'
}];

const OOTB_EDITORS = [{
  computeProfileEditor: 'aws-compute-profile-editor',
  networkProfileEditor: 'aws-network-profile-editor',
  storageProfileEditor: 'aws-storage-profile-editor'
}, {
  computeProfileEditor: 'azure-compute-profile-editor',
  networkProfileEditor: 'azure-network-profile-editor',
  storageProfileEditor: 'azure-storage-profile-editor'
}, {
  computeProfileEditor: 'vsphere-compute-profile-editor',
  networkProfileEditor: 'vsphere-network-profile-editor',
  storageProfileEditor: 'vsphere-storage-profile-editor'
}];

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
    let endpointType = this.model.item.endpoint &&
        this.model.item.endpoint.endpointType || this.model.item.endpointType;
    let tags = this.model.item.tags || [];
    return {
      computeProfileEditor: {
        properties: this.model.item.computeProfile || {},
        valid: false
      },
      networkProfileEditor: {
        properties: this.model.item.networkProfile || {},
        valid: false
      },
      storageProfileEditor: {
        properties: this.model.item.storageProfile || {},
        valid: false
      },
      currentView: 'basic',
      editorErrors: null,
      endpoint: this.model.item.endpoint,
      endpointType,
      name: this.model.item.name,
      supportedEditors: OOTB_EDITORS,
      supportedTypes: OOTB_TYPES,
      tags: tags.map(({key, value}) => ({
        key,
        value
      }))
    };
  },
  computed: {
    saveDisabled() {
      return !this.name || !this.endpointType;
    },
    validationErrors() {
      return this.model.validationErrors || this.editorErrors || {};
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
    onNameChange(value) {
      this.name = value;
    },
    onEndpointChange(endpoint) {
      this.endpoint = endpoint;
      this.endpointType = endpoint && endpoint.endpointType;
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    onComputeProfileEditorChange(value) {
      this.editorErrors = null;
      this.computeProfileEditor = value;
    },
    onNetworkProfileEditorChange(value) {
      this.editorErrors = null;
      this.networkProfileEditor = value;
    },
    onNetworkManageSubnetworks() {
      EnvironmentsActions.manageSubnetworks();
    },
    onStorageProfileEditorChange(value) {
      this.editorErrors = null;
      this.storageProfileEditor = value;
    },
    onEditorError(errors) {
      this.editorErrors = errors;
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
    getModel() {
      var toSave = $.extend({ properties: {} }, this.model.item.asMutable({deep: true}));
      toSave.name = this.name;
      toSave.endpointLink = this.endpoint && this.endpoint.documentSelfLink;
      toSave.computeProfile = $.extend(toSave.computeProfile || {},
          this.computeProfileEditor.properties);
      toSave.networkProfile = $.extend(toSave.networkProfile || {},
          this.networkProfileEditor.properties);
      toSave.storageProfile = $.extend(toSave.storageProfile || {},
          this.storageProfileEditor.properties);
      return toSave;
    }
  }
});
