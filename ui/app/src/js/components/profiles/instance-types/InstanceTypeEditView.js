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

import AwsInstanceTypeSearch from 'components/profiles/instance-types/AwsInstanceTypeSearch'; //eslint-disable-line
import AzureInstanceTypeSearch from 'components/profiles/instance-types/AzureInstanceTypeSearch'; //eslint-disable-line
import InstanceTypeSearch from 'components/profiles/instance-types/InstanceTypeSearch'; //eslint-disable-line
import VsphereInstanceType from 'components/profiles/instance-types/VsphereInstanceType'; //eslint-disable-line

import { ProfileActions } from 'actions/Actions';
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import InstanceTypeEditViewVue
  from 'components/profiles/instance-types/InstanceTypeEditViewVue.html';
import utils from 'core/utils';

export default Vue.component('instance-type-edit-view', {
  template: InstanceTypeEditViewVue,
  props: {
    model: {
      default: () => ({
        contextView: {},
        item: {}
      }),
      required: true,
      type: Object
    }
  },
  data() {
    return {
      adapters: utils.getAdapters(),
      name: null,
      endpoint: null,
      endpointType: null,
      editorErrors: null,
      instanceTypeMapping: [],
      tags: [],
      instanceTypeEditor: {
        properties: {},
        valid: true
      }
    };
  },
  computed: {
    saveDisabled() {
      return !this.name || !this.endpointType || !this.instanceTypeEditor.valid;
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
    this.unwatchItem = this.$watch('model.item', (item) => {
      if (item == null || item.endpoint == null) {
        return;
      }
      let endpointType = item.endpoint && item.endpoint.endpointType || item.endpointType;
      let tags = item.tags || [];

      this.endpoint = item.endpoint;
      this.endpointType = endpointType;
      this.instanceTypeMapping = item.instanceTypeMapping;
      this.name = item.name;
      this.tags = tags.map(({key, value}) => ({
        key,
        value
      }));

      this.unwatchItem();
    });
  },
  detached() {
    this.unwatchItem();
  },
  methods: {
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      let model = this.getModel();
      var tagRequest = utils.createTagAssignmentRequest(model.documentSelfLink,
          this.model.item.tags || [], this.tags);
      if (model.documentSelfLink) {
        ProfileActions.updateInstanceType(model, tagRequest);
      } else {
        ProfileActions.createInstanceType(model, tagRequest);
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
    onInstanceTypeEditorChange(value) {
      this.editorErrors = null;
      this.instanceTypeEditor = value;
    },
    onEditorError(errors) {
      this.editorErrors = errors;
    },
    createEndpoint() {
      ProfileActions.createEndpoint();
    },
    manageEndpoints() {
      ProfileActions.manageEndpoints();
    },
    closeToolbar() {
      ProfileActions.closeToolbar();
    },
    getModel() {
      // TODO: prepare a PATCH body instead of full-blown document
      var toSave = $.extend({ properties: {} }, this.model.item.asMutable({deep: true}));
      toSave.name = this.name;
      toSave.endpointLink = this.endpoint && this.endpoint.documentSelfLink;
      toSave.endpointType = this.endpoint && this.endpoint.endpointType;
      toSave.instanceTypeMapping = $.extend(toSave.instanceType || {},
          this.instanceTypeEditor.properties.instanceTypeMapping);
      toSave.tagLinks = undefined;
      return toSave;
    }
  }
});
