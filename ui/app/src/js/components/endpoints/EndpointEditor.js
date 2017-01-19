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

import VueDropdownInput from 'components/common/VueDropdownInput'; //eslint-disable-line
import VuePasswordInput from 'components/common/VuePasswordInput'; //eslint-disable-line
import VueTextInput from 'components/common/VueTextInput'; //eslint-disable-line
import AwsEndpointEditor from 'components/endpoints/aws/EndpointEditor'; //eslint-disable-line
import AzureEndpointEditor from 'components/endpoints/azure/EndpointEditor'; //eslint-disable-line
import NimbusEndpointEditor from 'components/endpoints/nimbus/EndpointEditor'; //eslint-disable-line
import OpenstackEndpointEditor from 'components/endpoints/openstack/EndpointEditor'; //eslint-disable-line
import VsphereEndpointEditor from 'components/endpoints/vsphere/EndpointEditor'; //eslint-disable-line
import EndpointEditorVue from 'components/endpoints/EndpointEditorVue.html';
import { EndpointsActions } from 'actions/Actions';
import utils from 'core/utils';
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

var loadExternalTypes = function() {
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

        externalAdapters.push({
          id: doc.id,
          name: doc.name,
          iconSrc: icon
        });
      }
    }
  });
};

var getAvailableAdapters = function() {
  let supportedTypes = OOTB_TYPES.slice();
  if (utils.isNimbusEnabled()) {
    supportedTypes.push({
      id: 'nimbus',
      name: 'Nimbus',
      iconSrc: 'image-assets/endpoints/nimbus.png'
    });
  }
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
          (this.propertiesErrors && this.propertiesErrors._generic);
    }
  },
  attached: function() {
    let supportedTypes = getAvailableAdapters();

    if (!externalAdapters) {
      if (utils.isExternalPhotonAdaptersEnabled()) {
        var loading = {
          id: 'loading',
          name: 'Loading',
          isBusy: true
        };

        supportedTypes.push(loading);
        loadExternalTypes().then(() => {
          this.supportedEndpointTypes = getAvailableAdapters();
        }).catch(() => {
          this.supportedEndpointTypes = getAvailableAdapters();
        });
      } else {
        externalAdapters = [];
      }
    }

    this.supportedEndpointTypes = supportedTypes;
  },
  data() {
    return {
      endpointType: this.model.item.endpointType,
      name: this.model.item.name,
      properties: this.model.item.endpointProperties || {},
      propertiesErrors: null,
      saveDisabled: !this.model.item.documentSelfLink,
      supportedEndpointTypes: []
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
    onPropertiesChange(properties) {
      this.properties = properties;
      this.saveDisabled = this.isSaveDisabled();
    },
    onPropertiesError(errors) {
      this.propertiesErrors = errors;
    },
    isSaveDisabled() {
      let model = this.getModel();
      if (!model.name || !model.endpointType) {
        return true;
      }
      let properties = model.endpointProperties;
      if (!properties.privateKeyId || !properties.privateKey || !properties.regionId) {
        return true;
      }
      if (model.endpointType === 'azure' && (!properties.userLink || !properties.azureTenantId)) {
          return true;
      }
      if (model.endpointType === 'vsphere' && !properties.hostName) {
          return true;
      }
      return false;
    },
    getModel() {
      return $.extend({}, this.model.item, {
        endpointProperties: $.extend(this.model.item.endpointProperties || {}, this.properties),
        endpointType: this.endpointType,
        name: this.name
      });
    },
    convertToObject(value) {
      if (value) {
        return {
          id: value,
          name: value
        };
      }
    }
  }
});
