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

import ClosureRequestFormVue from 'components/closures/ClosureRequestFormVue.html';
import ClosureDefinitionForm from 'components/closures/ClosureDefinitionForm';
import ResourcePoolsList from 'components/resourcepools/ResourcePoolsList'; //eslint-disable-line
import ClosureFieldsMixin from 'components/closures/ClosureFieldsMixin';
import {
  ClosureActions, TemplateActions, TemplatesContextToolbarActions
}
from 'actions/Actions';

var ClosureRequestForm = Vue.extend({
  template: ClosureRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          tasks: {},
          contextView: {},
          taskLogs: {},
          resourcePools: {}
        };
      }
    }
  },

  mixins: [ClosureFieldsMixin],

  data: function() {
    return {
      savingTask: true,
      resourcePool: null
    };
  },
  computed: {
    runningTask: function() {
      if (this.model.tasks.monitoredTask.isRunning) {
        return true;
      }

      return false;
    }
  },
  methods: {
    createClosureDescription: function() {
      console.log('Creating closure description...');
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTask = false;
        var closureDefinition = this.definitionForm.getClosureDefinition();
        if (this.resourcePool) {
          closureDefinition.resourcePoolId = this.resourcePool.id;
        }
        if (this.model.tasks.editingItemData == null) {
          ClosureActions.createClosure(this.model.documentId, closureDefinition);
        } else {
          closureDefinition.documentSelfLink = this.model.tasks.
          editingItemData.item.documentSelfLink;
          ClosureActions.editClosure(closureDefinition);
        }
      }
    },
    saveAndRunClosure: function() {
      console.log('Saving/Editing before execution closure...');
      let validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTask = false;
        let closureDefinition = this.definitionForm.getClosureDefinition();
        let inputs = this.definitionForm.getClosureInputs();
        if (this.resourcePool) {
          closureDefinition.resourcePoolId = this.resourcePool.id;
        }
        if (this.model.tasks.editingItemData == null) {
          ClosureActions.createAndRunClosure(this.model.documentId, closureDefinition, inputs);
        } else {
          this.runClosure();
        }
      }
    },
    runClosure: function() {
      console.log('Executing closure...');
      var validationErrors = this.definitionForm.validate();
      if (!validationErrors) {
        var closureDefinition = this.definitionForm.getClosureDefinition();
        var inputs = this.definitionForm.getClosureInputs();
        closureDefinition.documentSelfLink = this.model.tasks.editingItemData.item.documentSelfLink;
        console.log('Executing closure with description: ' + closureDefinition.documentSelfLink);
        TemplateActions.runClosure(closureDefinition, inputs);
        TemplatesContextToolbarActions.openToolbarClosureResults();
      } else {
        console.log('Validation error detected: ' + validationErrors);
      }
    },
    saveClosureTemplate: function() {
      console.log('Saving closure as template...');
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTemplate = true;
        var closureDefinition = this.definitionForm.getClosureDefinition();
        if (this.resourcePool) {
          closureDefinition.resourcePoolId = this.resourcePool.id;
        }
        TemplateActions.createClosureTemplate(closureDefinition);
      }
    },
    getEl: function() {
      return $(this.$el);
    }
  },
  attached: function() {

    this.definitionForm = new ClosureDefinitionForm();
    $(this.$el).find('.closure-definition-form').replaceWith(this.definitionForm.getEl());

    this.initializeClosureFields();

    this.unwatchModel = this.$watch('model', (data) => {
      if (data.tasks && data.tasks.editingItemData && data.tasks.editingItemData.resourcePool) {
        this.resourcePoolInput.setSelectedOption(data.tasks.editingItemData.resourcePool);
        this.resourcePool = this.resourcePoolInput.getSelectedOption();
        // this.disableInput('resourcePool', data.tasks.editingItemData.resourcePool.name);
      }

      if (data.tasks && data.tasks.editingItemData) {
        this.definitionForm.setData(data.tasks.editingItemData.item);

        var alertMessage = (data.error) ? data._generic : data.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage);
        }
      }
    }, {
      immediate: true
    });
  },
  detached: function() {
    this.unwatchModel();
    ClosureActions.resetMonitoredClosure();
    this.unwatchResourcePools();
    this.unwatchResourcePool();
  }
});

Vue.component('closure-request-form', ClosureRequestForm);

export
default ClosureRequestForm;
