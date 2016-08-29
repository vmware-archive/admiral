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

import EnvironmentPropertyEditorVue from 'EnvironmentPropertyEditorVue';
import { EnvironmentsActions } from 'actions/Actions';
import utils from 'core/utils';

var EnvironmentPropertyEditor = Vue.extend({
  template: EnvironmentPropertyEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    mappings: function() {
      return this.model.property.value ?
          utils.objectToArray(this.model.property.value.mappings) : [];
    }
  },
  attached: function() {
    this.toggleSaveBtnState = () =>
        saveBtn.attr('disabled', !this.nameInput.val());

    this.nameInput = $(this.$el).find('.nameInput > input');
    if (!this.nameInput.val() && !$('input:focus').size()) {
      this.nameInput.first().focus();
    }
    this.nameInput.on('change', this.toggleSaveBtnState);

    var saveBtn = $(this.$el).find('.environmentPropertyEdit-save');

    this.toggleSaveBtnState(saveBtn, this.nameInput);
  },
  detached: function() {
    this.nameInput.off('change', this.toggleSaveBtnState);
  },
  methods: {
    cancel: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      EnvironmentsActions.cancelEditEnvironmentProperty();
    },
    save: function($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      var toSave = $.extend({}, this.model.item.properties);
      delete toSave[this.model.property.name];

      toSave[this.nameInput.val()] = {
        mappings: utils.arrayToObject(this.$refs.mappings.getData())
      };
      EnvironmentsActions.updateEnvironmentProperties(toSave);
    }
  }
});

Vue.component('environment-property-editor', EnvironmentPropertyEditor);

export default EnvironmentPropertyEditor;
