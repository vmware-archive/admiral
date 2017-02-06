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

import TemplateImporterViewVue from 'components/templates/TemplateImporterViewVue.html';
import { TemplateActions } from 'actions/Actions';
import constants from 'core/constants';

var TemplateImporterView = Vue.extend({
  template: TemplateImporterViewVue,
  data: function() {
    return {
      templateContent: ''
    };
  },
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  attached: function() {
    this.unwatchModel = this.$watch('model', (model) => {
      if (model.error) {
        this.$dispatch('container-form-alert',
                       model.error._generic,
                       constants.ALERTS.TYPE.FAIL);
      } else {
        this.$dispatch('container-form-alert', null);
      }
    }, {immediate: true});
  },
  detached: function() {
    if (this.model.error) {
      this.$dispatch('container-form-alert', null);
    }
    this.unwatchModel();
  },
  methods: {
    browseFile: function($event) {
      $event.preventDefault();
      $event.stopImmediatePropagation();

      $(this.$el).find('input.upload').trigger('click');
    },
    onFileChange: function($event) {
      var files = $event.target.files;
      if (!files.length) {
        return;
      }
      this.loadFromFile(files[0]);
    },
    loadFromFile: function(file) {
      //TODO: file size validation

      var reader = new FileReader();

      reader.onload = (e) => {
        this.templateContent = e.target.result;
      };
      reader.readAsText(file);
    },
    importTemplate: function() {
      if (this.templateContent) {
        TemplateActions.importTemplate(this.templateContent);
      }
    }
  }
});

Vue.component('template-importer-view', TemplateImporterView);

export default TemplateImporterView;
