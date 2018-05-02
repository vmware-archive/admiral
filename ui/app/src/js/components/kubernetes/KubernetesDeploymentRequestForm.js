/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import KubernetesDeploymentRequestFormVue from
  'components/kubernetes/KubernetesDeploymentRequestFormVue.html';
import KubernetesDeploymentDefinitionForm from
  'components/kubernetes/KubernetesDeploymentDefinitionForm';
import { TemplateActions } from 'actions/Actions';
import constants from 'core/constants';

var KubernetesDeploymentRequestForm = Vue.extend({
  template: KubernetesDeploymentRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object
    },
    useResourceAction: {
      type: Boolean
    }
  },
  data: function() {
    return {
      creatingDeployment: false,
      savingTemplate: false
    };
  },
  computed: {
    buttonsDisabled: function() {
      return this.creatingDeployment || this.savingTemplate;
    }
  },
  methods: {
    createDeployment: function() {
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.creatingDeployment = true;
        var containerDescription = this.definitionForm.getContainerDescription();

        TemplateActions.provisionKubernetesDeploymentTemplate(containerDescription);
      }
    },
    saveTemplate: function() {
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTemplate = true;
        var containerDescription = this.definitionForm.getContainerDescription();
        TemplateActions.createKubernetesDeploymentTemplate(containerDescription);
      }
    }
  },
  attached: function() {
    this.definitionForm = new KubernetesDeploymentDefinitionForm();
    $(this.$el).find('.kubernetes-defintion-form').replaceWith(this.definitionForm.getEl());
    this.unwatchModel = this.$watch('model.definitionInstance', (data) => {
      this.creatingDeployment = false;
      this.savingTemplate = false;

      if (data) {
        this.definitionForm.setData(data);
        var alertMessage = (data.error) ? data.error._generic : data.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage, constants.ALERTS.TYPE.FAIL);
        }
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('kubernetes-request-form', KubernetesDeploymentRequestForm);

export default KubernetesDeploymentRequestForm;
