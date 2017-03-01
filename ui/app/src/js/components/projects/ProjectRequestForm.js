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

import ProjectRequestFormVue from 'components/projects/ProjectRequestFormVue.html';
import ProjectDefinitionForm from 'components/projects/ProjectDefinitionForm';
import { ResourceGroupsActions } from 'actions/Actions';

var ProjectRequestForm = Vue.extend({
  template: ProjectRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object
    },
    error: {
      required: true
    }
  },
  data: function() {
    return {
      operationInProgress: false,
      disableCreateButton: true
    };
  },
  computed: {
    hasError: function() {
      return this.error && this.error.trim() !== '';
    },
    isUpdate: function() {
      return this.model && this.model.documentSelfLink;
    },
    titleText: function() {
      if (this.isUpdate) {
        return i18n.t('app.template.details.editProject.updateTitle');
      } else {
        return i18n.t('app.template.details.editProject.createTitle');
      }
    },
    createOrUpdateButtonText: function() {
      if (this.isUpdate) {
        return i18n.t('app.template.details.editProject.update');
      } else {
        return i18n.t('app.template.details.editProject.create');
      }
    }
  },
  methods: {
    createOrUpdateProject: function() {
      var projectForm = this.$refs.projectEditForm;
      var validationErrors = projectForm.validate();
      if (!validationErrors) {
        var project = projectForm.getProjectDefinition();
        this.operationInProgress = true;
        if (this.isUpdate) {
          ResourceGroupsActions.updateGroup(project, true);
        } else {
          ResourceGroupsActions.createGroup(project, true);
        }
      }
    },

    requiredInputChanged: function(filledRequiredInputs) {
      this.disableCreateButton = !filledRequiredInputs;
    }

  },
  attached: function() {
    this.unwatchModel = this.$watch('model.error', (error) => {
      if (error) {
        this.operationInProgress = false;
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  },
  components: {
    projectDefinitionForm: ProjectDefinitionForm
  }
});


Vue.component('project-request-form', ProjectRequestForm);

export default ProjectRequestForm;
