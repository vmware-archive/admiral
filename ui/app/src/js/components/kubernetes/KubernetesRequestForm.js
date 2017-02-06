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

import KubernetesRequestFormVue from 'components/kubernetes/KubernetesRequestFormVue.html';
import ResourceGroupsMixin from 'components/templates/ResourceGroupsMixin';
import { KubernetesActions } from 'actions/Actions';

var KubernetesRequestForm = Vue.extend({
  template: KubernetesRequestFormVue,
  mixins: [ResourceGroupsMixin],
  props: {
    model: {
      required: true,
      type: Object
    },
    fromResource: {
      type: Boolean
    }
  },
  data: function() {
    return {
      creating: false,
      entitiesContent: ''
    };
  },
  computed: {
    disableCreatingButton: function() {
      var content = this.entitiesContent && this.entitiesContent.trim();
      return this.creating || !content;
    }
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
    },
    createEntities: function() {
      var content = this.entitiesContent && this.entitiesContent.trim();
      if (content) {
        this.handleGroup(KubernetesActions.createKubernetesEntities, [content]);
      }
    }
  },
  attached: function() {
    this.unwatchModel = this.$watch('model.definitionInstance', () => {
      this.creating = false;
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('kubernetes-request-form', KubernetesRequestForm);

export default KubernetesRequestForm;
