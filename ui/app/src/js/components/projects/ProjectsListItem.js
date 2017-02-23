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

import utils from 'core/utils';

import ProjectsListItemVue from 'components/projects/ProjectsListItemVue.html';
import AlertItemMixin from 'components/common/AlertItemMixin';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import { ResourceGroupsActions } from 'actions/Actions';

var ProjectsListItem = Vue.extend({
  template: ProjectsListItemVue,
  mixins: [AlertItemMixin, DeleteConfirmationSupportMixin],
  props: {
    model: {
      required: true
    },
    error: {
      required: true
    }
  },

  attached: function() {
    this.unwatchError = this.$watch('error', (error) => {
      if (error) {
        this.cancelRemoval();
        if (error._generic) {
          this.showCustomAlertMessage(error._generic);
        } else {
          this.showAlert('errors.projectsUnexpectedError');
        }
      }
    });
  },

  detached: function() {
    this.unwatchError();
  },

  methods: {

    showCustomAlertMessage: function(message) {
      this.alert.message = message;
      this.alert.show = true;
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    removeProjectClicked: function($event) {
      this.askConfirmation($event);
    },

    doRemoveProject: function() {
      this.confirmRemoval(ResourceGroupsActions.deleteGroup, [this.model, true]);
    }

  }
});

Vue.component('project-grid-item', ProjectsListItem);

export default ProjectsListItem;
