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

import MachinesViewVue from 'components/machines/MachinesViewVue.html';
import MachineItem from 'components/machines/MachineItem'; //eslint-disable-line
import MachineEditView from 'components/machines/MachineEditView'; //eslint-disable-line
import MachineDetailsView from 'components/machines/MachineDetailsView'; //eslint-disable-line
import RequestsList from 'components/requests/RequestsList'; //eslint-disable-line
import EventLogList from 'components/eventlog/EventLogList'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { MachineActions, MachinesContextToolbarActions, NavigationActions,
    RequestsActions, NotificationsActions } from 'actions/Actions';

export default Vue.component('machines-view', {
  template: MachinesViewVue,
  props: {
    model: {
      default: () => {
        return {
          contextView: {}
        };
      },
      required: true,
      type: Object
    }
  },
  data() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
  },
  mixins: [GridHolderMixin],
  attached() {

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
  detached() {
    this.unwatchExpanded();

    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    clearInterval(this.refreshRequestsInterval);
    clearInterval(this.notificationsInterval);
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
    searchSuggestions() {
      return constants.MACHINES.SEARCH_SUGGESTIONS;
    }
  },
  methods: {
    goBack() {
      NavigationActions.openMachines(this.model.listView && this.model.listView.queryOptions);
    },
    search(queryOptions) {
      NavigationActions.openMachines(queryOptions);
    },
    refresh() {
      MachineActions.openMachines(this.model.listView.queryOptions, true);
    },
    loadMore() {
      if (this.model.listView.nextPageLink) {
        MachineActions.openMachinesNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    addMachine() {
      NavigationActions.openAddMachine();
    },
    openToolbarRequests: MachinesContextToolbarActions.openToolbarRequests,
    openToolbarEventLogs: MachinesContextToolbarActions.openToolbarEventLogs,
    closeToolbar: MachinesContextToolbarActions.closeToolbar
  }
});
