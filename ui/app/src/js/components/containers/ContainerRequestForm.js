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

import ContainerRequestFormVue from 'ContainerRequestFormVue';
import ContainerDefinitionForm from 'components/containers/ContainerDefinitionForm';
import ResourceGroupsMixin from 'components/templates/ResourceGroupsMixin'; // eslint-disable-line
import { ContainerActions } from 'actions/Actions';

var ContainerRequestForm = Vue.extend({
  template: ContainerRequestFormVue,
  mixins: [ResourceGroupsMixin],
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data: function() {
    return {
      creatingContainer: false,
      savingTemplate: false
    };
  },
  computed: {
    buttonsDisabled: function() {
      return this.creatingContainer || this.savingTemplate;
    }
  },
  methods: {
    createContainer: function() {
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.creatingContainer = true;
        var containerDescription = this.definitionForm.getContainerDescription();

        this.handleGroup(ContainerActions.createContainerWithDetails, [containerDescription]);
      }
    },
    saveTemplate: function() {
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTemplate = true;
        var containerDescription = this.definitionForm.getContainerDescription();
        ContainerActions.createContainerTemplate(containerDescription);
      }
    }
  },
  attached: function() {
    this.definitionForm = new ContainerDefinitionForm();
    $(this.$el).find('.container-defintion-form').replaceWith(this.definitionForm.getEl());

    this.unwatchModel = this.$watch('model.definitionInstance', (data) => {
      this.creatingContainer = false;
      this.savingTemplate = false;

      this.toggleGroupsDisplay();

      if (data) {
        this.definitionForm.setData(data);

        var alertMessage = (data.error) ? data.error._generic : data.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage);
        }
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('container-request-form', ContainerRequestForm);

export default ContainerRequestForm;
