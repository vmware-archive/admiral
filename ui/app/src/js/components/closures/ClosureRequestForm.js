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
import CustomDropdownSearchMenu from 'components/common/CustomDropdownSearchMenu';
import constants from 'core/constants';
import {
  TemplateActions, TemplatesContextToolbarActions, ContainerActions, ContainersContextToolbarActions
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
          placementZones: {}
        };
      }
    },
    shownInTemplates: {
      type: Boolean
    }
  },

  data: function() {
    return {
      savingTask: true,
      placementZone: null
    };
  },
  computed: {
    runningTask: function() {
      return this.model.tasks && this.model.tasks.monitoredTask &&
        this.model.tasks.monitoredTask.isRunning;
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
        if (this.placementZone) {
          closureDefinition.placementZoneId = this.placementZone.id;
        }
        if (this.model.tasks.editingItemData) {
          closureDefinition.documentSelfLink =
            this.model.tasks.editingItemData.item.documentSelfLink;
        }
        if (this.shownInTemplates) {
          TemplateActions.saveClosure(this.model.documentId, closureDefinition);
        } else {
          ContainerActions.saveClosure(closureDefinition);
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
        if (this.placementZone) {
          closureDefinition.placementZoneId = this.placementZone.id;
        }
        if (this.model.tasks.editingItemData) {
          closureDefinition.documentSelfLink =
            this.model.tasks.editingItemData.item.documentSelfLink;
        }
        if (this.shownInTemplates) {
          TemplateActions.runClosure(this.model.documentId, closureDefinition, inputs);
          TemplatesContextToolbarActions.openToolbarClosureResults();
        } else {
          ContainerActions.runClosure(closureDefinition, inputs);
          ContainersContextToolbarActions.openToolbarClosureResults();
        }
      }
    },
    saveClosureTemplate: function() {
      console.log('Saving closure as template...');
      var validationErrors = this.definitionForm.validate();
      this.definitionForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingTemplate = true;
        var closureDefinition = this.definitionForm.getClosureDefinition();
        if (this.placementZone) {
          closureDefinition.placementZoneId = this.placementZone.id;
        }
        TemplateActions.createClosureTemplate(closureDefinition);
      }
    },
    getEl: function() {
      return $(this.$el);
    },
    initializeClosureFields: function() {
      // Resource pool input
      var elemPlacementZone = $(this.$el).find('.placementZone .form-control');
      this.placementZoneInput = new CustomDropdownSearchMenu(elemPlacementZone, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.placementZone.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.placementZone.entity')
        })
      });

      var _this = this;
      this.placementZoneInput.setOptionSelectCallback(function(option) {
        _this.placementZone = option;
      });

      this.unwatchPlacementZones = this.$watch('model.placementZones', () => {
        if (this.model.placementZones === constants.LOADING) {
          this.placementZoneInput.setLoading(true);
        } else {
          this.placementZoneInput.setLoading(false);
          this.placementZoneInput.setOptions(
          (this.model.placementZones || []).map((config) => config.resourcePoolState));
        }
      }, {immediate: true});

      this.unwatchPlacementZone = this.$watch('model.placementZone', () => {
        if (this.model.placementZone) {
          this.placementZoneInput.setSelectedOption(this.model.placementZone.resourcePoolState);
        }
      }, {immediate: true});

    }
  },
  attached: function() {

    this.definitionForm = new ClosureDefinitionForm();
    $(this.$el).find('.closure-definition-form').replaceWith(this.definitionForm.getEl());

    this.initializeClosureFields();

    this.unwatchModel = this.$watch('model', (data) => {
      if (data.tasks && data.tasks.editingItemData && data.tasks.editingItemData.placementZone) {
        this.placementZoneInput.setSelectedOption(
          data.tasks.editingItemData.placementZone.resourcePoolState);
        this.placementZone = this.placementZoneInput.getSelectedOption();
        // this.disableInput('placementZone', data.tasks.editingItemData.placementZone.name);
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
    this.unwatchPlacementZones();
    this.unwatchPlacementZone();
    TemplateActions.resetMonitoredClosure();
    ContainerActions.resetMonitoredClosure();
  }
});

Vue.component('closure-request-form', ClosureRequestForm);

export
default ClosureRequestForm;
