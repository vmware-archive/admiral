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

import utils from 'core/utils';
import constants from 'core/constants';

import NetworksListItemVue from 'NetworksListItemVue'; //eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import { NetworkActions, NavigationActions } from 'actions/Actions';

const possibleDay2Operations = [
  constants.RESOURCES.NETWORKS.OPERATION.REMOVE
];

var NetworksListItem = Vue.extend({
  template: NetworksListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },

  computed: {

    hasConnectedContainers: function() {
      return this.model.connectedContainers
              && this.model.connectedContainers.length > 0;
    },

    connectedContainersCount: function() {
      return this.hasConnectedContainers
              ? this.model.connectedContainers.length : 0;
    },

    connectedContainersDocumentIds: function() {
      return !this.hasConnectedContainers
              ? [] : this.model.connectedContainers.map(
        (container) => {
          return container.documentId;
        });
    },

    hasParentHosts: function() {
      return this.model.parentLinks
              && this.model.parentLinks.length > 0;
    },

    parentHostsCount: function() {
      return this.hasParentHosts
              ? this.model.parentLinks.length : 0;
    },

    supportsDay2Operations: function() {
      return possibleDay2Operations.some(
        (operation) => {
          if (this.operationSupported(operation)) {
            return true;
          }
        });
    }

  },
  methods: {

    getNetworkDocumentId: function() {
      return this.model.documentId;
    },

    openConnectedContainers: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      if (this.hasConnectedContainers) {
        let queryOptions = {
          network: this.model.name
        };

        NavigationActions.openContainers(queryOptions);
      }
    },

    removeNetwork: function() {
      this.confirmRemoval(NetworkActions.removeNetwork,
                          [this.getNetworkDocumentId()]);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    networkStatusDisplay: utils.networkStatusDisplay

  }
});

Vue.component('network-grid-item', NetworksListItem);

export default NetworksListItem;
