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

import HostsViewVue from 'components/hosts/HostsViewVue.html';
import HostItemVue from 'components/hosts/HostItem'; //eslint-disable-line
import HostView from 'components/hosts/HostView'; //eslint-disable-line
import VueAdapter from 'components/common/VueAdapter';
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import utils from 'core/utils';
import { HostActions, HostsContextToolbarActions, RequestsActions,
        NotificationsActions, NavigationActions } from 'actions/Actions';

var HostsViewVueComponent = Vue.extend({
  template: HostsViewVue,

  props: {
    model: {
      required: true,
      type: Object,

      default: () => {
        return {
          hostsView: {},
          contextView: {},
          hostAddView: null
        };
      }
    }
  },

  data: function() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
  },

  computed: {
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    requestsCount: function() {
      var contextView = this.model.contextView;
      if (contextView && contextView.notifications) {
        return contextView.notifications.requests;
      }
      return 0;
    },
    eventLogsCount: function() {
      var contextView = this.model.contextView;
      if (contextView && contextView.notifications) {
        return contextView.notifications.eventlogs;
      }
      return 0;
    },
    messages: function() {
      return this.model.validationErrors;
    },
    alertMessage: function() {
      return this.messages ? this.messages._valid : null;
    },
    errorMessage: function() {
      return this.messages ? this.messages._generic : null;
    },
    searchSuggestions: function() {
      return constants.HOSTS.SEARCH_SUGGESTIONS;
    }
  },

  mixins: [GridHolderMixin],

  attached: function() {
    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');

    $mainPanel.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $mainPanel[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );

    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($mainPanel);
      });
    });

    this.refreshRequestsInterval = setInterval(() => {
      if (this.activeContextItem === constants.CONTEXT_PANEL.REQUESTS) {
        RequestsActions.refreshRequests();
      }
    }, constants.REQUESTS.REFRESH_INTERVAL);

    this.notificationsInterval = setInterval(() => {
      if (!this.contextExpanded) {
        NotificationsActions.retrieveNotifications();
      }
    }, constants.NOTIFICATIONS.REFRESH_INTERVAL);
  },

  detached: function() {
    this.unwatchExpanded();

    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    clearInterval(this.refreshRequestsInterval);
    clearInterval(this.notificationsInterval);
  },

  methods: {
    addHost: function() {
      NavigationActions.openAddHost();
    },
    goBack: function() {
      NavigationActions.openHosts(this.model.listView && this.model.listView.queryOptions);
    },
    search: function(queryOptions) {
      NavigationActions.openHosts(queryOptions);
    },
    refresh: function() {
      HostActions.openHosts(this.model.listView.queryOptions);
    },
    loadMore: function() {
      if (this.model.listView.nextPageLink) {
        HostActions.openHostsNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    operationSupported: function(op) {
      return utils.operationSupportedDataCollect(op);
    },
    triggerDataCollection: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      HostActions.triggerDataCollection();
    },
    openToolbarRequests: HostsContextToolbarActions.openToolbarRequests,
    openToolbarEventLogs: HostsContextToolbarActions.openToolbarEventLogs,
    closeToolbar: HostsContextToolbarActions.closeToolbar
  }
});

const TAG_NAME = 'hosts-view';
Vue.component(TAG_NAME, HostsViewVueComponent);

function HostsView($el) {
  return new VueAdapter($el, TAG_NAME);
}

export default HostsView;
