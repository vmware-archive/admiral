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

import EndpointEditorVue from 'EndpointEditorVue';
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
  data: function() {
    return {
      saveDisabled: true
    };
  },
  methods: {
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
      this.saveDisabled = !model.name || !model.endpointType ||
        !model.privateKey || !model.privateKeyId || !model.regionId;
    },
    getModel: function() {
      var toSave = $.extend({}, this.model.item);

      toSave.name = $(this.$el).find('.nameInput > input').val();
      var selectedType = this.typeInputDropdown.getSelectedOption();
      toSave.endpointType = selectedType && selectedType.id;
      toSave.privateKey = $(this.$el).find('.secretAccessKey > input').val();
      toSave.privateKeyId = $(this.$el).find('.accessKeyId > input').val();
      toSave.regionId = $(this.$el).find('.regionIdInput > input').val();
      toSave.endpointHost = $(this.$el).find('.endpointHost > input').val();

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
    this.typeInputDropdown.setOptionSelectCallback(this.onInputChange);

    this.unwatchType = this.$watch('model.item.endpointType', (type) => {
      var typeInstance = null;
      if (type) {
        for (var i = 0; i < TYPES.length; i++) {
          if (TYPES[i].id === type) {
            typeInstance = TYPES[i];
            break;
          }
        }
      }
      this.typeInputDropdown.setSelectedOption(typeInstance);
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchType();
    this.typeInputDropdown = null;
  }
});

Vue.component('endpoint-editor', EndpointEditor);

export default EndpointEditor;
