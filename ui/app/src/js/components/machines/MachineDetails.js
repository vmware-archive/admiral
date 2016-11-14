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

import MachineDetailsVue from 'components/machines/MachineDetailsVue.html';
import MaximizableBehaviour from 'components/common/MaximizableBehaviour'; //eslint-disable-line
import MachineProperties from 'components/machines/MachineProperties'; //eslint-disable-line
import utils from 'core/utils';

var MachineDetails = Vue.extend({
  template: MachineDetailsVue,
  data: function() {
  },

  props: {
    model: { required: true }
  },

  computed: {
    hasOperationError: function() {
      return this.model.operationFailure && (this.model.operationFailure != null);
    },
    hasGeneralError: function() {
      return this.model.error && this.model.error._generic;
    },
    generalError: function() {
      return this.hasGeneralError ? this.model.error._generic : '';
    }
  },

  components: {
    'toolbar-action-button': {
      template: `<div class="toolbar-action" v-show="supported">
           <a class="btn btn-circle"><i class="fa fa-{{iconName}}"></i></a>
           <div class="toolbar-action-label">{{label}}</div>
         </div>`,
      props: {
        iconName: {
          required: true,
          type: String
        },
        label: {
          required: true,
          type: String
        },
        supported: {
          required: false,
          type: Boolean,
          default: true
        }
      }
    }
  },

  methods: {
    getMachine: function() {
      return this.model.instance;
    },

    statusDisplay: utils.containerStatusDisplay,

    rebootMachine: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='refresh'
    },

    startMachine: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='refresh'
    },

    stopMachine: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='refresh'
    },

    removeMachine: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='refresh'
    },

    getOperationInProgress: function() {
      return this.model.operationInProgress;
    },

    hasOperationInProgress: function() {
      var op = this.getOperationInProgress();

      return op && (op != null);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.getMachine());
    },

    refresh: function() {
      // No backend support yet, icon-name='refresh'
    }
  }
});

Vue.component('machine-details', MachineDetails);

export default MachineDetails;
