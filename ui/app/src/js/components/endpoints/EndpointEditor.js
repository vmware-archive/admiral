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

import EndpointEditorVue from 'components/endpoints/EndpointEditorVue.html';
import { EndpointsActions } from 'actions/Actions';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
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

var EndpointEditor = Vue.extend({
  template: EndpointEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    isEditMode: function() {
      return this.model.item.documentSelfLink;
    },
    endpointProperties: function() {
      return this.model.item.endpointProperties || {};
    },
    supportedEndpointTypes: function() {
      var supportedTypes = OOTB_TYPES.slice();

      if (utils.isNimbusEnabled()) {
        supportedTypes.push({
          id: 'nimbus',
          name: 'Nimbus',
          iconSrc: 'image-assets/endpoints/nimbus.png'
        });
      }

      return supportedTypes;
    }
  },
  data: function() {
    return {
      saveDisabled: true,
      currentEndpointType: null
    };
  },
  methods: {
    showInput: function(etype) {
      return this.currentEndpointType === etype;
    },
    cancel: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EndpointsActions.cancelEditEndpoint();
    },
    save: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      var toSave = this.getModel();

      if (toSave.documentSelfLink) {
        EndpointsActions.updateEndpoint(toSave);
      } else {
        EndpointsActions.createEndpoint(toSave);
      }
    },
    onInputChange: function() {
      Vue.nextTick(() => {
        var model = this.getModel();

        this.saveDisabled = !model.name || !model.endpointType || !model.endpointProperties;
        if (!this.saveDisabled) {
          // check specific properties
          //  - region
          let isRegionSupported = !this.isSelected('vsphere') && !this.isSelected('nimbus');
          let noRegion = (isRegionSupported && !model.endpointProperties.regionId);
          //  - authn properties
          // username === privateKeyId
          let isPasswordSupported = !this.isSelected('nimbus');
          let noAuthData = !model.endpointProperties.privateKeyId
                            || (isPasswordSupported && !model.endpointProperties.privateKey);

          this.saveDisabled = noRegion || noAuthData;
        }
      });
    },
    isSelected: function(endpointType) {
      let selectedEndpointType = this.typeInputDropdown.getSelectedOption();
      let selectedEndpointTypeId = selectedEndpointType && selectedEndpointType.id;

      return selectedEndpointTypeId === endpointType;
    },
    getModel: function() {
      var toSave = $.extend({}, this.model.item);

      toSave.name = $(this.$el).find('.nameInput > input').val();
      var selectedType = this.typeInputDropdown.getSelectedOption();
      toSave.endpointType = selectedType && selectedType.id;

      var props = {};

      if (toSave.endpointProperties) {
        props = toSave.endpointProperties.asMutable();
      }

      props.privateKey = $(this.$el).find('.secretAccessKey > input').val();
      let privateKeyId = $(this.$el).find('.accessKeyId > input').val();
      if (this.isSelected('nimbus')) {
        props.userEmail = privateKeyId;
      }

      props.privateKeyId = privateKeyId;

      props.regionId = $(this.$el).find('.regionIdInput > input').val();
      props.hostName = $(this.$el).find('.endpointHostInput > input').val();
      props.userLink = $(this.$el).find('.subscriptionIdInput > input').val();
      props.azureTenantId = $(this.$el).find('.tenantIdInput > input').val();

      toSave.endpointProperties = props;

      return toSave;
    }
  },
  attached: function() {
    var typeHolder = $(this.$el).find('.typeInput .dropdown-holder');
    this.typeInputDropdown = new DropdownSearchMenu(typeHolder, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.endpoint.typeEntity')
      }),
      searchDisabled: true
    });

    this.typeInputDropdown.setOptions(this.supportedEndpointTypes);
    this.typeInputDropdown.setOptionSelectCallback(() => {
      var selectedType = this.typeInputDropdown.getSelectedOption();
      this.currentEndpointType = selectedType && selectedType.id;
      this.onInputChange();
    });

    this.unwatchType = this.$watch('model.item.endpointType', (type) => {
      var typeInstance = null;
      if (type) {
        for (var i = 0; i < this.supportedEndpointTypes.length; i++) {
          if (this.supportedEndpointTypes[i].id === type) {
            typeInstance = this.supportedEndpointTypes[i];
            break;
          }
        }

        this.currentEndpointType = type;
      }
      this.typeInputDropdown.setSelectedOption(typeInstance);
    }, {immediate: true});

    this.unwatchIsEditMode = this.$watch('isEditMode', (isEditMode) => {
      this.typeInputDropdown.setDisabled(isEditMode);
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchType();
    this.unwatchIsEditMode();
    this.typeInputDropdown = null;
  }
});

Vue.component('endpoint-editor', EndpointEditor);

export default EndpointEditor;
