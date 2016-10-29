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

import CompositeContainerDetailsVue from 'CompositeContainerDetailsVue';
import ContainersListItem from 'components/containers/ContainersListItem'; //eslint-disable-line
import ClusterContainersListItem from 'components/containers/cluster/ClusterContainersListItem';  //eslint-disable-line
import ContainerDetails from 'components/containers/ContainerDetails'; //eslint-disable-line
import ClusterContainerDetails from 'components/containers/cluster/ClusterContainerDetails';//eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import NetworkConnectorMixin from 'components/templates/NetworkConnectorMixin';
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var CompositeContainerDetails = Vue.extend({
  template: CompositeContainerDetailsVue,
  mixins: [GridHolderMixin, NetworkConnectorMixin],
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

    this.unwatchSelectedItem = this.$watch('model.selectedItem', () => {
      this.repositionListView();
    });
    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($detailsContent);
      });
    });
    this.unwatchNetworks = this.$watch('networks', (networks, oldNetworks) => {
      if (networks !== oldNetworks) {
        this.networksChanged(networks);
      }
    });
    this.unwatchNetworkLinks = this.$watch('networkLinks', (networkLinks, oldNetworkLinks) => {
      if (networkLinks !== oldNetworkLinks) {
        Vue.nextTick(() => {
          this.applyContainerToNetworksLinks(networkLinks);
        });
      }
    });

    this.setNetworksReadOnly(true);
  },
  detached: function() {
    this.unwatchSelectedItem();
    this.unwatchExpanded();
    this.unwatchNetworks();
    this.unwatchNetworkLinks();
    var $detailsContent = $(this.$el);
    $detailsContent.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');
  },
  methods: {
    openCompositeContainerDetails: function() {
      NavigationActions.openCompositeContainerDetails(this.model.documentId);
    },

    openCompositeContainerChildDetails: function(childId) {
      NavigationActions.openContainerDetails(childId, null, this.model.documentId);
    },

    openCompositeContainerClusterDetails: function(clusterId) {
      NavigationActions.openClusterDetails(clusterId, this.model.documentId);
    },

    repositionListView: function() {
      Vue.nextTick(() => {
        var $el = $(this.$el);
        var $smallContextHolder = $el
          .children('.list-view').children('.selected-context-small-holder');
        var top = '';
        if ($smallContextHolder.length === 1) {
          top = $smallContextHolder.position().top + $smallContextHolder.height();
        }
        $(this.$el)
          .children('.list-view').children('.grid-container')
          .css({top: top});
      });
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
    removeApplication: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.removeCompositeContainer(this.model.documentId);
    },
    networksChanged: function(networks) {
      var gridChildren = this.$refs.containerGrid.$children;
      gridChildren.forEach((child) => {
        if (child.$children && child.$children.length === 1) {
          var container = child.$children[0];
          if (container.model && container.model.documentSelfLink) {
            this.updateContainerEndpoints(networks, container.model.documentSelfLink);
          }
        }
      });
      this.onLayoutUpdate();
    },
    containerAttached: function(e) {
      var containerDescriptionLink = e.model.documentSelfLink;
      this.prepareContainerEndpoints($(e.$el).find('.container-networks')[0],
                                     containerDescriptionLink);
    },
    networkAttached: function(e) {
      var networkDescriptionLink = e.model.documentSelfLink;
      var networkAnchor = $(e.$el).find('.network-anchor')[0];
      this.addNetworkEndpoint(networkAnchor, networkDescriptionLink);
    },
    networkDetached: function(e) {
      var networkAnchor = $(e.$el).find('.network-anchor')[0];
      this.removeNetworkEndpoint(networkAnchor);
    },
    layoutComplete: function() {
      setTimeout(() => {
        this.onLayoutUpdate();
      }, 500);
    }
  },
  filters: {
    networksOrderBy: function(items) {
      var priorityNetworks = [constants.NETWORK_MODES.HOST.toLowerCase(),
                              constants.NETWORK_MODES.BRIDGE.toLowerCase()];

      if (items.asMutable) {
        items = items.asMutable();
      }
      return items.sort(function(a, b) {
        var aName = a.name.toLowerCase();
        var bName = b.name.toLowerCase();
        for (var i = 0; i < priorityNetworks.length; i++) {
          var net = priorityNetworks[i];
          if (net === aName) {
            return -1;
          }
          if (net === bName) {
            return 1;
          }
        }

        return aName.localeCompare(bName);
      });
    }
  }
});

Vue.component('composite-container-details', CompositeContainerDetails);

export default CompositeContainerDetails;

