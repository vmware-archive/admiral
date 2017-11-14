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

import CompositeContainerDetailsVue from
  'components/containers/composite/CompositeContainerDetailsVue.html';
import ContainersListItem from 'components/containers/ContainersListItem'; //eslint-disable-line
import ClusterContainersListItem from 'components/containers/cluster/ClusterContainersListItem';  //eslint-disable-line
import ContainerDetails from 'components/containers/ContainerDetails'; //eslint-disable-line
import ClusterContainerDetails from 'components/containers/cluster/ClusterContainerDetails';//eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import ConnectorMixin from 'components/templates/ConnectorMixin';
import ResourceConnectionsDataMixin from 'components/templates/ResourceConnectionsDataMixin';
import ActionConfirmationSupportMixin from 'components/common/ActionConfirmationSupportMixin';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import constants from 'core/constants'; //eslint-disable-line
import ft from 'core/ft';
import utils from 'core/utils';

import { ContainerActions, NavigationActions } from 'actions/Actions';

var CompositeContainerDetails = Vue.extend({
  template: CompositeContainerDetailsVue,
  mixins: [
    GridHolderMixin, ConnectorMixin,
    ResourceConnectionsDataMixin, DeleteConfirmationSupportMixin,
    ActionConfirmationSupportMixin
  ],
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
  data: function() {
    return {
      constants: constants
    };
  },
  computed: {
    contextExpanded: function() {
      return this.$parent.model.contextView && this.$parent.model.contextView.expanded;
    },
    selectedItemDocumentId: function() {
      return this.model.selectedItem && this.model.selectedItem.documentId;
    },
    networks: function() {
      var networks = this.model.listView && this.model.listView.networks;
      return networks || [];
    },
    networkLinks: function() {
      var networkLinks = this.model.listView && this.model.listView.networkLinks;
      return networkLinks || {};
    },
    volumes: function() {
      var volumes = this.model.listView && this.model.listView.volumes;
      return volumes || [];
    },
    volumeLinks: function() {
      var volumeLinks = this.model.listView && this.model.listView.volumeLinks;
      return volumeLinks || {};
    }
  },
  attached: function() {
    var $detailsContent = $(this.$el);
    $detailsContent.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $detailsContent[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );
    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($detailsContent);
      });
    });

    this.setResourcesReadOnly(true);

    if (ft.allowHostEventsSubscription()) {
      this.refreshContainersInterval = setInterval(() => {
        if (!this.$parent.model.rescanningContainer) {
          ContainerActions.rescanApplicationContainers(this.model.documentId);
        }
      }, utils.getContainersRefreshInterval());

      if (!this.startRefreshPollingTimeout) {
        this.startRefreshPollingTimeout = setTimeout(
          () => this.refreshContainersInterval, constants.CONTAINERS.START_REFRESH_POLLING_DELAY);
      }
    }
  },
  detached: function() {
    this.unwatchExpanded();
    this.unwatchNetworks();
    this.unwatchNetworkLinks();

    var $detailsContent = $(this.$el);
    $detailsContent.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    if (ft.allowHostEventsSubscription()) {
      clearTimeout(this.startRefreshPollingTimeout);
      this.startRefreshPollingTimeout = null;

      clearInterval(this.refreshContainersInterval);
      this.refreshContainersInterval = null;

      ContainerActions.stopRescanApplicationContainers();
    }
  },
  methods: {
    goBack: function() {
      this.$dispatch('go-back', 'application-details');
    },
    openCompositeContainerDetails: function() {
      NavigationActions.openCompositeContainerDetails(this.model.documentId);
    },

    openCompositeContainerChildDetails: function(childId) {
      NavigationActions.openContainerDetails(childId, null, this.model.documentId);
    },

    openCompositeContainerClusterDetails: function(clusterId) {
      NavigationActions.openClusterDetails(clusterId, this.model.documentId);
    },

    refresh: function() {
      ContainerActions.openCompositeContainerDetails(this.model.documentId);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    startApplication: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.startCompositeContainer(this.model.documentId);
    },

    stopApplication: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.stopCompositeContainer(this.model.documentId);
    },

    handleConfirmation: function(actionName) {
      // remove application
      if (actionName === 'removeApplication') {
        ContainerActions.removeCompositeContainer(this.model.documentId);
      }
    },

    layoutComplete: function() {
      setTimeout(() => {
        this.onLayoutUpdate();
      }, 500);
    }
  },

  events: {

    'go-back': function(fromViewName) {
      if (fromViewName === 'container-details'
        || fromViewName === 'cluster-details') {

        return this.openCompositeContainerDetails();
      }
    }
  }
});

Vue.component('composite-container-details', CompositeContainerDetails);

export default CompositeContainerDetails;

