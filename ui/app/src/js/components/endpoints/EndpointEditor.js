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

import AwsEndpointEditor from 'components/endpoints/aws/EndpointEditor'; //eslint-disable-line
import AzureEndpointEditor from 'components/endpoints/azure/EndpointEditor'; //eslint-disable-line
import VsphereEndpointEditor from 'components/endpoints/vsphere/EndpointEditor'; //eslint-disable-line
import EndpointEditorVue from 'components/endpoints/EndpointEditorVue.html';
import { EndpointsActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';

export default Vue.component('endpoint-editor', {
  template: EndpointEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    validationErrors() {
      return (this.model.validationErrors && this.model.validationErrors._generic) ||
          (this.editorErrors && this.editorErrors._generic);
    }
  },
  data() {
    return {
      adapters: utils.getAdapters(),
      editor: {
        properties: this.model.item.endpointProperties || {},
        valid: false
      },
      editorErrors: null,
      endpointType: this.model.item.endpointType,
      name: this.model.item.name,
      saveDisabled: !this.model.item.documentSelfLink
    };
  },
  methods: {
    cancel($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      EndpointsActions.cancelEditEndpoint();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      let toSave = this.getModel();
      if (toSave.documentSelfLink) {
        EndpointsActions.updateEndpoint(toSave);
      } else {
        EndpointsActions.createEndpoint(toSave);
      }
    },
    collectInventory($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      services.collectInventory(this.model.item);
    },
    collectImages($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      services.collectImages(this.model.item);
    },
    onNameChange(name) {
      this.name = name;
      this.saveDisabled = this.isSaveDisabled();
    },
    onEndpointTypeChange(endpointType) {
      this.endpointType = endpointType && endpointType.id;
      this.saveDisabled = this.isSaveDisabled();
    },
    onEditorChange(editor) {
      this.editor = editor;
      this.editorErrors = null;
      this.saveDisabled = this.isSaveDisabled();
    },
    onEditorError(errors) {
      this.editorErrors = errors;
    },
    isSaveDisabled() {
      return !this.name || !this.endpointType || !this.editor.valid;
    },
    getModel() {
      return $.extend({}, this.model.item, {
        endpointProperties: $.extend({}, this.model.item.endpointProperties || {},
            this.editor.properties),
        endpointType: this.endpointType,
        name: this.name
      });
    },
    convertToObject(value) {
      if (value) {
        return {
          id: value,
          name: this.adapters.find((type) => type.id === value).name
        };
      }
    }
  }
});
