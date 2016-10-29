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

import ClusterContainersListItemVue from 'ClusterContainersListItemVue'; //eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import { ContainerActions, NavigationActions } from 'actions/Actions'; //eslint-disable-line
import utils from 'core/utils';

var ClusterContainersListItem = Vue.extend({
  template: ClusterContainersListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },
  data: function() {
    return {
      clicksNumber: 0
    };
  },
  computed: {
    containersNamesToString: function() {
      var containersNamesStr = '';

      if (this.model.containers) {
        for (let idx = 0; idx < this.model.containers.length; idx++) {
          let container = this.model.containers[idx];
          let containerNames = container.names.join(', ');

          containersNamesStr = containersNamesStr.concat(containerNames);

          if (idx < this.model.containers.length - 1) {
            containersNamesStr = containersNamesStr.concat(', ');
          }
        }
      }

      return containersNamesStr;
    },
    clusterSize: function() {
      return this.model.containers && this.model.containers.length;
    }
  },
  attached: function() {
    this.$dispatch('attached', this);
  },
  methods: {
    openCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let clusterId = this.model.documentId;
      let compositeComponentId = this.model.compositeComponentId;

      NavigationActions.openClusterDetails(clusterId, compositeComponentId);
    },

    modifyClusterSize: function($event, incrementValue) {
      $event.stopPropagation();
      $event.preventDefault();

      this.clicksNumber += incrementValue;

      clearTimeout(this.timerClicks);

      var $this = this;
      this.timerClicks = setTimeout(() => {

        let desiredClusterSize = $this.model.containers.length + $this.clicksNumber;
        // clear number of clicks
        $this.clicksNumber = 0;

        if (desiredClusterSize > 0) {

          ContainerActions.modifyClusterSize($this.model.descriptionLink, desiredClusterSize);
        }
      }, 500);
    },

    startCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.startCluster(this.model.containers);
    },

    stopCluster: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.stopCluster(this.model.containers);
    },

    removeCluster: function() {
      this.confirmRemoval(ContainerActions.removeCluster, [this.model.containers]);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }
  }
});

Vue.component('cluster-grid-item', ClusterContainersListItem);

export default ClusterContainersListItem;

