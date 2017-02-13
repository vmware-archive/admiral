/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import KubernetesDefinitionFormVue from 'components/kubernetes/KubernetesDefinitionFormVue.html';
import constants from 'core/constants';

var KubernetesDefinitionForm = Vue.extend({
  template: KubernetesDefinitionFormVue,
  props: {
    model: {
      type: Object
    },
    disabled: {
      type: Boolean
    },
    showTitle: {
      type: Boolean
    }
  },
  data: function() {
    return {
      creating: false,
      entitiesContent: ''
    };
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
        this.entitiesContent = e.target.result;
      };
      reader.readAsText(file);
    }
  },
  attached: function() {
    this.unwatchModel = this.$watch('model', (model) => {
      this.creating = false;
      var alertMessage = model && model.error && model.error._generic;
      if (alertMessage) {
        this.$dispatch('container-form-alert', alertMessage, constants.ALERTS.TYPE.FAIL);
      }

      if (model && model.kubernetesEntity) {
        this.entitiesContent = model.kubernetesEntity;
      }

    }, {immediate: true});

    this.unwatcEntitiesContent = this.$watch('entitiesContent', (entitiesContent) => {
      this.$emit('content-change', entitiesContent);
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
    this.unwatcEntitiesContent();
  }
});

export default KubernetesDefinitionForm;
