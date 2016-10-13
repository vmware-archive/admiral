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
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var CompositeContainerDetails = Vue.extend({
  template: CompositeContainerDetailsVue,
  mixins: [GridHolderMixin],
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
  },
  detached: function() {
    this.unwatchSelectedItem();
    this.unwatchExpanded();
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
    }
  }
});

Vue.component('composite-container-details', CompositeContainerDetails);

export default CompositeContainerDetails;

