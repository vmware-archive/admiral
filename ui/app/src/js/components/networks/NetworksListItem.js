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

import NetworksListItemVue from 'components/networks/NetworksListItemVue.html';
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
    model: {
      required: true
    }, showAlertContainersConnected: {
      required: true
    }
  },

  data: function() {
    return {
      alert: {
        type: 'warning',
        show: false,
        message: ''
      }
    };
  },

  computed: {
    connectedContainersCount: function() {
      return this.model.connectedContainersCount ? this.model.connectedContainersCount : 0;
    },

    applicationsCount: function() {
      return this.model.compositeComponentLinks ? this.model.compositeComponentLinks.length : 0;
    },

    parentHostsCount: function() {
      return this.model.parentLinks ? this.model.parentLinks.length : 0;
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

  attached: function() {
    this.unwatchShowAlertContainersConnected = this.$watch('showAlertContainersConnected', () => {
      if (this.showAlertContainersConnected) {
        this.showNetworkRemovalContainersConnectedAlert();
      }
    });
  },

  detached: function() {
    this.unwatchShowAlertContainersConnected();
  },

  methods: {
    getNetworkDocumentId: function() {
      return this.model.documentId;
    },

    openConnectedContainers: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let queryOptions = {
        network: this.model.name
      };

      NavigationActions.openContainers(queryOptions);
    },

    removeNetworkClicked: function($event) {
      if (utils.isNetworkRemovalPossible(this.model)) {

        this.askConfirmation($event);
      } else {
        $event.stopPropagation();
        $event.preventDefault();

        this.showNetworkRemovalContainersConnectedAlert();
      }
    },

    doRemoveNetwork: function() {
      this.confirmRemoval(NetworkActions.removeNetwork,
                          [this.getNetworkDocumentId()]);
    },

    showNetworkRemovalContainersConnectedAlert: function() {
      this.alert.message =
        i18n.t('app.resource.list.network.operations.errors.remove.containersConnected');
      this.alert.show = true;
    },

    alertClosed: function() {
      this.alert.show = false;
      this.alert.message = '';
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    showHosts: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let hostIds = this.model.parentLinks.map((parentLink) => utils.getDocumentId(parentLink));
      let queryOptions = {
        any: hostIds
      };

      NavigationActions.openHosts(queryOptions);
    },

    showApps: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let appIds = this.model.compositeComponentLinks.map((appLink) =>
                                                            utils.getDocumentId(appLink));
      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.APPLICATIONS,
        $occurrence: constants.SEARCH_OCCURRENCE.ANY,
        documentId: appIds
      };

      NavigationActions.openContainers(queryOptions, true);
    },

    networkStatusDisplay: utils.networkStatusDisplay
  }
});

Vue.component('network-grid-item', NetworksListItem);

export default NetworksListItem;
