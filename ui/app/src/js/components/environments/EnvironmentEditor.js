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

import EnvironmentEditorVue from 'components/environments/EnvironmentEditorVue.html';
import { EnvironmentsActions } from 'actions/Actions';
import EnvironmentPropertyEditor from 'components/environments/EnvironmentPropertyEditor'; //eslint-disable-line
import utils from 'core/utils';

var mappingsToString = function(mappings) {
  return Object.keys(mappings).map((key) =>
    key.concat('=', mappings[key])).join(',');
};

var EnvironmentEditor = Vue.extend({
  template: EnvironmentEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data: function() {
    var sortOrders = {
      name: 1,
      mappings: 1
    };
    return {
      sortKey: '',
      sortOrders: sortOrders
    };
  },
  computed: {
    properties: function() {
      return utils.objectToArray(this.model.item.properties);
    }
  },
  filters: {
    mappingsToString: mappingsToString,
    propertiesOrderBy: function(properties, sortKey, reverse) {
      if (!sortKey) {
        return properties;
      }
      var order = reverse && reverse < 0 ? -1 : 1;

      return properties.sort(function(a, b) {

        if (sortKey === 'mappings') {
          a = mappingsToString(a.value.mappings);
          b = mappingsToString(b.value.mappings);
        } else {
          a = a[sortKey];
          b = b[sortKey];
        }
        if (!a) {
          a = '';
        }
        if (!b) {
          b = '';
        }
        return a.toLowerCase().localeCompare(b.toLowerCase()) * order;
      });
    }
  },
  attached: function() {
    this.toggleSaveBtnState = () =>
        this.saveBtn.attr('disabled', !this.nameInput.val() || !this.endpointTypeInput.val() ||
        !(this.model.item.properties && Object.keys(this.model.item.properties).length));

    this.nameInput = $(this.$el).find('.nameInput > input');
    if (!this.nameInput.val()) {
      this.nameInput.first().focus();
    }
    this.nameInput.on('change', this.toggleSaveBtnState);

    this.endpointTypeInput = $(this.$el).find('.endpointTypeInput > select');
    this.endpointTypeInput.val(this.model.item.endpointType || '');
    this.endpointTypeInput.on('change', this.toggleSaveBtnState);

    this.saveBtn = $(this.$el).find('.environmentEdit-save');

    this.unwatchProperties = this.$watch('model.item.properties', () => {
      this.toggleSaveBtnState();
    });

    this.toggleSaveBtnState();
  },
  detached: function() {
    this.nameInput.off('change', this.toggleSaveBtnState);
    this.endpointTypeInput.off('change', this.toggleSaveBtnState);
    this.unwatchProperties();
  },
  methods: {
    isEditingProperty: function(property) {
      var editingProperty = this.model && this.model.property;
      return editingProperty && editingProperty.name === property.name;
    },
    isEditingNewProperty: function() {
      var editingProperty = this.model && this.model.property;
      return editingProperty && !editingProperty.name;
    },
    addNewProperty: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.editEnvironmentProperty({});
    },
    editProperty: function(property, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.editEnvironmentProperty(property);
    },
    deleteProperty: function(property, $event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      var toSave = $.extend({}, this.model.item.properties);
      delete toSave[property.name];

      EnvironmentsActions.updateEnvironmentProperties(toSave);
    },
    sortBy: function(key) {
      this.sortKey = key;
      this.sortOrders[key] = this.sortOrders[key] * -1;
    },
    cancel: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.cancelEditEnvironment();
    },
    save: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      var toSave = $.extend({}, this.model.item);

      toSave.name = this.nameInput.val();
      toSave.endpointType = this.endpointTypeInput.val();

      if (toSave.documentSelfLink) {
        EnvironmentsActions.updateEnvironment(toSave);
      } else {
        EnvironmentsActions.createEnvironment(toSave);
      }
    }
  }
});

Vue.component('environment-editor', EnvironmentEditor);

export default EnvironmentEditor;
