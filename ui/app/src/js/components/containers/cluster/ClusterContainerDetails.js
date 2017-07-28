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

import ClusterContainerDetailsVue from
  'components/containers/cluster/ClusterContainerDetailsVue.html';
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import ClusterContainersListItem from 'components/containers/cluster/ClusterContainersListItem';  //eslint-disable-line
import ContainersListItem from 'components/containers/ContainersListItem'; //eslint-disable-line
import ContainerDetails from 'components/containers/ContainerDetails'; //eslint-disable-line
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var ClusterContainerDetails = Vue.extend({
  template: ClusterContainerDetailsVue,
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
    },
    parentCompositeComponentId: {
      required: false,
      type: String
    }
  },
  data: function() {
    return {
      constants: constants
    };
  },
  computed: {
    selectedItemDocumentId: function() {
      return this.model.selectedItem && this.model.selectedItem.documentId;
    }
  },
  methods: {
    goBack: function() {
      this.$dispatch('go-back', 'cluster-details');
    },

    openContainerDetails: function(containerId) {
      NavigationActions.openContainerDetails(containerId, this.model.documentId,
                                              this.parentCompositeComponentId);
    },

    openClusterDetails: function() {
      NavigationActions.openClusterDetails(this.model.documentId,
                                              this.parentCompositeComponentId);
    },

    refresh: function() {
      NavigationActions.openClusterDetails(this.model.documentId, this.parentCompositeComponentId);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    startCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.startCluster(this.model.listView.items);
    },
    stopCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.stopCluster(this.model.listView.items);
    },
    removeCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.removeCluster(this.model.listView.items);
    }
  },

  events: {
    'go-back': function(fromViewName) {
      if (fromViewName === 'container-details') {
        return this.openClusterDetails();
      }
    }
  }
});

Vue.component('cluster-container-details', ClusterContainerDetails);

export default ClusterContainerDetails;

