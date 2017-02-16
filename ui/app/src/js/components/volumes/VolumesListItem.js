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

import VolumesListItemVue from 'components/volumes/VolumesListItemVue.html';
import AlertItemMixin from 'components/common/AlertItemMixin';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import { VolumeActions, NavigationActions } from 'actions/Actions';

const possibleDay2Operations = [
  constants.RESOURCES.VOLUMES.OPERATION.REMOVE,
  constants.RESOURCES.VOLUMES.OPERATION.MANAGE
];

var VolumesListItem = Vue.extend({
  template: VolumesListItemVue,
  mixins: [AlertItemMixin, DeleteConfirmationSupportMixin],
  props: {
    model: {required: true},
    showAlertContainersConnected: {required: true},
    showAlertManagedByCatalog: {required: true}
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
        this.showAlertConnectedContainers();
      }
    });

    this.unwatchShowAlertManagedByCatalog = this.$watch('showAlertManagedByCatalog', () => {
      if (this.showAlertManagedByCatalog) {
        this.showAlert('errors.managedByCatalog');
      }
    });
  },

  detached: function() {
    this.unwatchShowAlertContainersConnected();
    this.unwatchShowAlertManagedByCatalog();
  },

  methods: {
    getVolumeDocumentId: function() {
      return this.model.documentId;
    },

    openConnectedContainers: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let queryOptions = {
        volume: this.model.name
      };

      // TODO
      NavigationActions.openContainers(queryOptions);
    },

    manageVolume: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let documentId = this.getVolumeDocumentId();

      VolumeActions.openManageVolumes(documentId);
    },

    removeVolume: function() {
      this.confirmRemoval(VolumeActions.removeVolume, [this.getVolumeDocumentId()]);
    },

    confirmVolumeRemoval: function($event) {
      if (utils.canRemove(this.model)) {

        this.askConfirmation($event);
      } else {
        $event.stopPropagation();
        $event.preventDefault();

        this.showAlertConnectedContainers();
      }
    },

    showAlertConnectedContainers: function() {
      this.showAlert('errors.containersConnected');
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    showHosts: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let hostIds = this.model.parentLinks.map((parentLink) => utils.getDocumentId(parentLink));
      let queryOptions = {
        $occurrence: constants.SEARCH_OCCURRENCE.ANY,
        documentId: hostIds
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

    volumeStatusDisplay: utils.networkStatusDisplay
  }
});

Vue.component('volume-grid-item', VolumesListItem);

export default VolumesListItem;
