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
import ft from 'core/ft';
import services from 'core/services';

const OOTB_TYPES = [
  {
    id: 'aws',
    name: 'AWS',
    iconSrc: 'image-assets/endpoints/aws.png'
  },
  {
    id: 'azure',
    name: 'Azure',
    iconSrc: 'image-assets/endpoints/azure.png'
  },
  {
    id: 'vsphere',
    name: 'vSphere',
    iconSrc: 'image-assets/endpoints/vsphere.png'
  }
];

var externalAdapters = null;

var loadExternalAdapters = function() {
  return services.loadAdapters().then((adapters) => {
    externalAdapters = [];
    if (adapters) {
      for (var k in adapters) {
        if (!adapters.hasOwnProperty(k)) {
          continue;
        }
        var doc = adapters[k];

        var icon = doc.customProperties && doc.customProperties.icon;
        if (icon && icon[0] === '/') {
          // Remove slash, as UI is not always served at /, e.g. in CAFE embedded.
          // So instead use relative path.
          icon = icon.substring(1);
        }

        var uiLink = doc.customProperties && doc.customProperties.uiLink;
        if (uiLink && uiLink[0] === '/') {
          // Remove slash, as UI is not always served at /, e.g. in CAFE embedded.
          // So instead use relative path.
          uiLink = uiLink.substring(1);
        }

        var endpointEditor = doc.customProperties && doc.customProperties.endpointEditor;

        externalAdapters.push({
          id: doc.id,
          name: doc.name,
          iconSrc: icon,
          uiLink: uiLink,
          endpointEditor: endpointEditor
        });
      }
    }

    return Promise.all(externalAdapters.map(({uiLink}) =>
        services.loadScript(uiLink)));
  });
};

var getSupportedEditors = function() {
  let supportedEditors = [
    'aws-endpoint-editor',
    'azure-endpoint-editor',
    'vsphere-endpoint-editor'
  ];
  if (externalAdapters) {
    supportedEditors = supportedEditors.concat(
        externalAdapters.map(({endpointEditor}) => endpointEditor));
  }
  return supportedEditors;
};

var getSupportedTypes = function() {
  let supportedTypes = OOTB_TYPES.slice();
  if (externalAdapters) {
    supportedTypes = supportedTypes.concat(externalAdapters);
  }
  return supportedTypes;
};

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
  attached() {
    let supportedEditors = getSupportedEditors();
    let supportedTypes = getSupportedTypes();

    if (!externalAdapters) {
      if (ft.isExternalPhotonAdaptersEnabled()) {
        var loading = {
          id: 'loading',
          name: 'Loading',
          isBusy: true
        };

        supportedTypes.push(loading);
        loadExternalAdapters().then(() => {
          this.supportedEditors = getSupportedEditors();
          this.supportedTypes = getSupportedTypes();
        }).catch(() => {
          this.supportedEditors = getSupportedEditors();
          this.supportedTypes = getSupportedTypes();
        });
      } else {
        externalAdapters = [];
      }
    }

    this.supportedEditors = supportedEditors;
    this.supportedTypes = supportedTypes;
  },
  data() {
    return {
      editor: {
        properties: this.model.item.endpointProperties || {},
        valid: false
      },
      editorErrors: null,
      endpointType: this.model.item.endpointType,
      name: this.model.item.name,
      saveDisabled: !this.model.item.documentSelfLink,
      supportedEditors: [],
      supportedTypes: []
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
        endpointProperties: $.extend(this.model.item.endpointProperties || {},
            this.editor.properties),
        endpointType: this.endpointType,
        name: this.name
      });
    },
    convertToObject(value) {
      if (value) {
        return {
          id: value,
          name: getSupportedTypes().find((type) => type.id === value).name
        };
      }
    }
  }
});
