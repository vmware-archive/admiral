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

import ContainersViewVue from 'ContainersViewVue';
import ContainersListItem from 'components/containers/ContainersListItem'; //eslint-disable-line
import ClusterContainersListItem from 'components/containers/cluster/ClusterContainersListItem';  //eslint-disable-line
import CompositeContainersListItem from 'components/containers/composite/CompositeContainersListItem'; //eslint-disable-line
import ContainerDetails from 'components/containers/ContainerDetails';//eslint-disable-line
import ClusterContainerDetails from 'components/containers/cluster/ClusterContainerDetails';//eslint-disable-line
import CompositeContainerDetails from 'components/containers/composite/CompositeContainerDetails';//eslint-disable-line
import RequestsList from 'components/requests/RequestsList';//eslint-disable-line
import EventLogList from 'components/eventlog/EventLogList';//eslint-disable-line
import VueAdapter from 'components/common/VueAdapter';
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { NavigationActions, RequestsActions, NotificationsActions,
          ContainerActions, ContainersContextToolbarActions } from 'actions/Actions';

var ContainersViewVueComponent = Vue.extend({
  template: ContainersViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          listView: {},
          contextView: {}
        };
      }
    }
  },
  computed: {
    isSelectedCategoryApplications: function() {
      return this.selectedCategory === constants.CONTAINERS.SEARCH_CATEGORY.APPLICATIONS;
    },
    hasContainerDetailsError: function() {
      return this.model.selectedItemDetails.error && this.model.selectedItemDetails.error._generic;
    },
    containerDetailsError: function() {
      return this.hasContainerDetailsError ? this.model.selectedItemDetails.error._generic : '';
    },
    showContextPanel: function() {
      var selectedItemDetails = this.model.selectedItemDetails;
      if (!selectedItemDetails) {
        // nothing is selected to view its details
        return true;
      }
      while (selectedItemDetails.selectedItemDetails) {
        // composite/cluster item has been selected for details viewing
        selectedItemDetails = selectedItemDetails.selectedItemDetails;
      }

      return selectedItemDetails.type !== constants.CONTAINERS.TYPES.SINGLE;
    },
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    queryOptions: function() {
      return this.model.listView && this.model.listView.queryOptions;
    },
    selectedCategory: function() {
      var queryOpts = this.queryOptions || {};
      return queryOpts[constants.SEARCH_CATEGORY_PARAM] ||
        constants.CONTAINERS.SEARCH_CATEGORY.CONTAINERS;
    },
    selectedItemDocumentId: function() {
      return this.model.selectedItem && this.model.selectedItem.documentId;
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
    }
  },
  data: function() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
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

    this.unwatchSelectedItem = this.$watch('model.selectedItem', () => {
        this.repositionListView();
    });

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
    this.unwatchSelectedItem();

    this.unwatchExpanded();

    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    ContainerActions.closeContainers();

    clearInterval(this.refreshRequestsInterval);
    clearInterval(this.notificationsInterval);
  },

  methods: {
    goBack: function() {
      NavigationActions.openContainers(this.queryOptions);
    },

    search: function(queryOptions) {
      this.doSearchAndFilter(queryOptions, this.selectedCategory);
    },

    selectCategory(categoryName, $event) {
      this.doSearchAndFilter(this.queryOptions, categoryName);
      $event.stopPropagation();
      $event.preventDefault();
    },

    doSearchAndFilter: function(queryOptions, categoryName) {
      var queryOptionsToSend = $.extend({}, queryOptions);
      queryOptionsToSend[constants.SEARCH_CATEGORY_PARAM] = categoryName;

      NavigationActions.openContainers(queryOptionsToSend);
    },

    openContainerDetails: function(documentId) {
      NavigationActions.openContainerDetails(documentId);
    },

    openClusterDetails: function(clusterId) {
      NavigationActions.openClusterDetails(clusterId);
    },

    openCompositeContainerDetails: function(documentId) {
      NavigationActions.openCompositeContainerDetails(documentId);
    },

    repositionListView: function() {
      Vue.nextTick(() => {
        var $smallContextHolder = $(this.$el)
          .find('.main-panel > .list-view > .selected-context-small-holder');
        var top = '';
        if ($smallContextHolder.length === 1) {
          top = $smallContextHolder.position().top + $smallContextHolder.height();
        }
        $(this.$el).find('.main-panel > .list-view > .grid-container').css({top: top});
      });
    },

    refresh: function() {
      ContainerActions.openContainers(this.queryOptions, true);
    },

    loadMore: function() {
      if (this.model.listView.nextPageLink) {
        ContainerActions.openContainersNext(this.queryOptions,
          this.model.listView.nextPageLink);
      }
    },

    openToolbarRequests: ContainersContextToolbarActions.openToolbarRequests,
    openToolbarEventLogs: ContainersContextToolbarActions.openToolbarEventLogs,
    closeToolbar: ContainersContextToolbarActions.closeToolbar
  }
});

const TAG_NAME = 'containers-view';
Vue.component(TAG_NAME, ContainersViewVueComponent);

function ContainersView($el) {
  return new VueAdapter($el, TAG_NAME);
}

export default ContainersView;
