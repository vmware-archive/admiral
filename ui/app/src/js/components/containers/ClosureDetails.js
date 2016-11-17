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

import ClosureDetailsVue from 'components/containers/ClosureDetailsVue.html';
import Component from 'components/common/Component';
import MaximizableBehaviour from 'components/common/MaximizableBehaviour'; //eslint-disable-line
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import ClosureProperties from 'components/containers/ClosureProperties'; //eslint-disable-line
import ClosureDescriptionProperties from 'components/containers/ClosureDescriptionProperties'; //eslint-disable-line
import ActionConfirmationSupportMixin from 'components/common/ActionConfirmationSupportMixin'; //eslint-disable-line
import { ContainerActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';

var ClosureDetailsVueComponent = Vue.extend({
  template: ClosureDetailsVue,
  data: function() {
    return {
      logsSinceDurations: constants.CONTAINERS.LOGS.SINCE_DURATIONS
    };
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

  mixins: [ActionConfirmationSupportMixin],

  methods: {
    getClosure: function() {
      return this.model.instance;
    },

    getClosureId: function() {
      return this.getClosure().documentId;
    },

    handleConfirmation: function(actionName) {
      if (actionName === 'removeClosureRun') {
        ContainerActions.removeClosureRun(this.getClosure().documentSelfLink);
        ContainerActions.openContainers({
          '$category': 'closures'
        }, true);
      }
    },

    getOperationInProgress: function() {
      return this.model.operationInProgress;
    },

    hasOperationInProgress: function() {
      var op = this.getOperationInProgress();

      return op && (op != null);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.getContainer());
    },

    refresh: function() {
      // ContainerActions.refreshContainer();
    },

    containerStatusDisplay: utils.containerStatusDisplay
  },
  components: {
    'logs-scroll': {
      template: '<div></div>',
      props: {
        logs: {required: true}
      },
      attached: function() {
        this.logsUnwatch = this.$watch('logs', (logs) => {
          var scrolledToBottom = (this.$el.scrollTop / this.$el.scrollHeight) > 0.95;
          Vue.nextTick(() => {
            this.$el.textContent = atob(logs);
            if (scrolledToBottom) {
              this.$el.scrollTop = this.$el.scrollHeight;
            }
          });
        }, {immediate: true});
      },
      detached: function() {
        this.logsUnwatch();
      }
    }
  }
});

Vue.component('closure-details', ClosureDetailsVueComponent);

class ClosureDetails extends Component {
  constructor() {
    super();
    this.$el = $('<div>').append('<closure-details v-bind:model="currentModel">');
  }

  getEl() {
    return this.$el;
  }

  attached() {
    this.vue = new Vue({
      el: this.$el[0],
      data: {
        currentModel: {}
      }
    });
  }

  detached() {
    if (this.vue) {
      this.vue.$destroy();
      this.vue = null;
    }
  }

  setData(data) {
    Vue.set(this.vue, 'currentModel', data);
  }
}

export default ClosureDetails;
