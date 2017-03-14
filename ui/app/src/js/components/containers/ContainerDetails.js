/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import ContainerDetailsVue from 'components/containers/ContainerDetailsVue.html';
import Component from 'components/common/Component';
import MaximizableBehaviour from 'components/common/MaximizableBehaviour'; //eslint-disable-line
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import ContainerProperties from 'components/containers/ContainerProperties'; //eslint-disable-line
import ContainerStats from 'components/containers/ContainerStats'; //eslint-disable-line
import ContainerShell from 'components/containers/ContainerShell'; //eslint-disable-line
import ActionConfirmationSupportMixin from 'components/common/ActionConfirmationSupportMixin'; //eslint-disable-line

import { ContainerActions, NavigationActions } from 'actions/Actions';

import constants from 'core/constants';
import utils from 'core/utils';
import ansi from 'ansi_up';

const REFRESH_STATS_TIMEOUT = 60000;
const REFRESH_LOGS_TIMEOUT = 5000;
const START_REFRESH_POLLING_DELAY = 2000;

var ContainerDetailsVueComponent = Vue.extend({
  template: ContainerDetailsVue,
  data: function() {
    return {
      logsSinceDurations: constants.CONTAINERS.LOGS.SINCE_DURATIONS,
      logsFormat: constants.CONTAINERS.LOGS.FORMAT,
      showShell: false,
      shellUrl: null
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
    },
    logsSettingsFormat: function() {
      return this.model.logsSettings && this.model.logsSettings.format;
    }
  },

  mixins: [ActionConfirmationSupportMixin],

  attached: function() {
    this.modelUnwatch = this.$watch('model', this.updateData, {immediate: true});
  },

  detached: function() {
    stopRefreshPolling.call(this);
    clearTimeout(this.initialLogsAndStatsTimeout);
    this.modelUnwatch();
  },

  methods: {
    getContainer: function() {
      return this.model.instance;
    },

    getContainerId: function() {
      return this.getContainer().documentId;
    },

    updateData: function(newData, oldData) {
      if (newData.instance && newData.instance.powerState === constants.CONTAINERS.STATES.RUNNING) {
        if (!this.startRefreshPollingTimeout) {
          this.startRefreshPollingTimeout = setTimeout(
            () => maybeStartRefreshPolling.call(this), START_REFRESH_POLLING_DELAY);
        }
      } else {
        stopRefreshPolling.call(this);
      }

      if (!oldData || oldData.documentId !== newData.documentId) {
        clearTimeout(this.initialLogsAndStatsTimeout);
        this.initialLogsAndStatsTimeout = setTimeout(() => {
          ContainerActions.refreshContainerStats();
          ContainerActions.refreshContainerLogs();
        }, START_REFRESH_POLLING_DELAY);
      }

      let newShellData = newData.shell;
      if (!newShellData) {
        $(this.$refs.shellModal.$el).next('.modal').find('iframe').remove();
      }
      this.showShell = newShellData && (newShellData !== (oldData && oldData.shell)) || false;
      this.shellUrl = this.showShell ? newShellData.shellUri : null;
    },

    containerStatusDisplay: utils.containerStatusDisplay,

    onLogsSinceChange: function(event) {
      var sinceDuration = $(event.target).val();
      ContainerActions.changeLogsSinceDuration(sinceDuration);
    },

    onLogsFormatChange: function(event) {
      var format = $(event.target).val();
      ContainerActions.changeLogsFormat(format);
    },

    cloneContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='copy'
    },

    rebootContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      // No backend support yet, icon-name='refresh'
    },

    startContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.startContainerDetails(this.getContainerId());
    },

    stopContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.stopContainerDetails(this.getContainerId());
    },

    openTemplate: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      var templateId = utils.getDocumentId(this.model.templateLink);

      NavigationActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE,
        templateId);
    },

    createTemplateFromContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.createTemplateFromContainer(this.getContainer());
    },

    handleConfirmation: function(actionName) {

      if (actionName === 'removeContainer') {
        ContainerActions.removeContainerDetails(this.getContainerId());
      }
    },
    openShell: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.openShell(this.model.documentId);
    },

    getOperationInProgress: function() {
      return this.model.operationInProgress;
    },

    hasOperationInProgress: function() {
      return !!this.getOperationInProgress();
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.getContainer());
    },

    refresh: function() {
      ContainerActions.refreshContainer();
    },

    goBack: function() {
      this.$dispatch('go-back', 'container-details');
    }
  },
  events: {
    'close-shell-modal': function() {
      ContainerActions.closeShell();
    }
  },
  components: {
    'logs-scroll': {
      template: '<div></div>',
      props: {
        logs: {required: true},
        format: {required: true}
      },
      attached: function() {
        this.logsUnwatch = this.$watch('logs', this.updateLogs, {immediate: true});
        this.formatUnwatch = this.$watch('format', this.updateLogs, {immediate: true});
      },
      detached: function() {
        this.logsUnwatch();
        this.formatUnwatch();
      },
      methods: {
        updateLogs: function() {
          if (this.updatePending) {
            return;
          }

          this.updatePending = true;

          var scrolledToBottom = (this.$el.scrollTop / this.$el.scrollHeight) > 0.95;
          Vue.nextTick(() => {
            this.updatePending = false;

            // set the text content, regardless of the format. if the format is ansi,
            // we want to use this method to escape html tags
            this.$el.textContent = this.logs;
            if (this.format === constants.CONTAINERS.LOGS.FORMAT.ANSI) {
              var logsEscaped = this.$el.innerHTML;
              if (logsEscaped) {
                this.$el.innerHTML = ansi.ansi_to_html(logsEscaped);
              }
            }
            if (scrolledToBottom) {
              this.$el.scrollTop = this.$el.scrollHeight;
            }
          });
        }
      }
    }
  }
});

var maybeStartRefreshPolling = function() {
  if (!this.refreshStatsInterval) {
    this.refreshStatsInterval = setInterval(() => {
      ContainerActions.refreshContainerStats();
    }, REFRESH_STATS_TIMEOUT);
  }

  if (!this.refreshLogsInterval) {
    this.refreshLogsInterval = setInterval(() => {
      ContainerActions.refreshContainerLogs();
    }, REFRESH_LOGS_TIMEOUT);
  }
};

var stopRefreshPolling = function() {
  clearTimeout(this.startRefreshPollingTimeout);
  this.startRefreshPollingTimeout = null;

  clearInterval(this.refreshStatsInterval);
  this.refreshStatsInterval = null;

  clearInterval(this.refreshLogsInterval);
  this.refreshLogsInterval = null;
};

Vue.component('container-details', ContainerDetailsVueComponent);

class ContainerDetails extends Component {
  constructor() {
    super();
    this.$el = $('<div>').append('<container-details v-bind:model="currentModel">');
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

export default ContainerDetails;
