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

const TYPES = [
  {
    id: 'aws',
    name: 'AWS',
    iconSrc: 'image-assets/endpoints/aws.png'
  },
  {
    id: 'gcp',
    name: 'GCP',
    iconSrc: 'image-assets/endpoints/gcp.png'
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
      var model = this.getModel();
      this.saveDisabled = !model.name || !model.endpointType || !model.endpointProperties ||
        !model.endpointProperties.privateKey || !model.endpointProperties.privateKeyId ||
        (!model.endpointProperties.regionId && !(model.endpointType === 'vsphere'));
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
      props.privateKeyId = $(this.$el).find('.accessKeyId > input').val();
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

    this.typeInputDropdown.setOptions(TYPES);
    this.typeInputDropdown.setOptionSelectCallback(() => {
      var selectedType = this.typeInputDropdown.getSelectedOption();
      this.currentEndpointType = selectedType && selectedType.id;
      this.onInputChange();
    });

    this.unwatchType = this.$watch('model.item.endpointType', (type) => {
      var typeInstance = null;
      if (type) {
        for (var i = 0; i < TYPES.length; i++) {
          if (TYPES[i].id === type) {
            typeInstance = TYPES[i];
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
