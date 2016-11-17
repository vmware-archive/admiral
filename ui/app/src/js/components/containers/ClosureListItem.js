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

import ClosureListItemVue from 'components/containers/ClosureListItemVue.html'; //eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import {
  ContainerActions, NavigationActions, AppActions
} from 'actions/Actions';

var ClosureListItem = Vue.extend({
  template: ClosureListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {
      required: true
    }
  },
  computed: {
    documentId: function() {
      return utils.getDocumentId(this.model.documentSelfLink);
    },
    inputs: function() {
      return JSON.stringify(this.model.inputs);
    },
    outputs: function() {
      return JSON.stringify(this.model.outputs);
    }
  },
  methods: {
    containerStatusDisplay: utils.containerStatusDisplay,

    getClosureRunId: function() {
      return this.model.documentId;
    },

    openClosureDetails: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      NavigationActions.openClosureDetails(this.getClosureRunId());
    },

    removeClosureRun: function() {
      this.confirmRemoval(ContainerActions.removeClosureRun, [this.model.documentSelfLink]);
      AppActions.openView(constants.VIEWS.RESOURCES.VIEWS.CLOSURES.name);
      ContainerActions.openContainers({
        '$category': 'closures'
      }, true);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }
  }
});

Vue.component('closure-grid-item', ClosureListItem);

export default ClosureListItem;
