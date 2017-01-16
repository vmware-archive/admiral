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

export default Vue.component('endpoint-editor', {
  template: EndpointEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    supportedEndpointTypes() {
      let supportedTypes = OOTB_TYPES.slice();
      if (utils.isNimbusEnabled()) {
        supportedTypes.push({
          id: 'nimbus',
          name: 'Nimbus',
          iconSrc: 'image-assets/endpoints/nimbus.png'
        });
      }
      if (utils.isOpenstackEnabled()) {
        supportedTypes.push({
          id: 'openstack',
          name: 'OpenStack',
          iconSrc: 'image-assets/endpoints/openstack.png'
        });
      }
      return supportedTypes;
    },
    validationErrors() {
      return (this.model.validationErrors && this.model.validationErrors._generic) ||
          (this.propertiesErrors && this.propertiesErrors._generic);
    }
  },
  data() {
    return {
      endpointType: this.model.item.endpointType,
      name: this.model.item.name,
      properties: this.model.item.endpointProperties || {},
      propertiesErrors: null,
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
