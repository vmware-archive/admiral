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

import ContainersListItemVue from 'components/containers/ContainersListItemVue.html';
import AlertItemMixin from 'components/common/AlertItemMixin';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import constants from 'core/constants';
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var ContainersListItem = Vue.extend({
  template: ContainersListItemVue,
  mixins: [AlertItemMixin, DeleteConfirmationSupportMixin],
  props: {
    model: {required: true},
    showAlertManagedByCatalog: {required: true}
  },
  computed: {
    portsDisplayTexts: function() {
      return utils.getPortsDisplayTexts(this.model.hostAddress, this.model.ports);
    },
    networkCount: function() {
      return this.model.networks && Object.keys(this.model.networks).length || 0;
    },
    applicationId: function() {
      return this.model.compositeComponentLink
               ? utils.getDocumentId(this.model.compositeComponentLink) : null;
    },
    networkIds: function() {
      return this.model.networks && Object.keys(this.model.networks);
    }
  },
  attached: function() {
    this.$dispatch('attached', this);

    this.unwatchShowAlertManagedByCatalog = this.$watch('showAlertManagedByCatalog', () => {
      if (this.showAlertManagedByCatalog) {
        this.showAlert('errors.managedByCatalog');
      }
    });
  },

  detached: function() {
    this.unwatchShowAlertManagedByCatalog();
  },

  methods: {
    containerStatusDisplay: utils.containerStatusDisplay,

    getContainerId: function() {
      return this.model.documentId;
    },

    openContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();
      let containerId = this.getContainerId();
      let clusterId = this.model.clusterId;
      if (clusterId) {
        let compositeComponentId = this.model.customProperties
                                  && this.model.customProperties.__composition_context_id;
        NavigationActions.openContainerDetails(containerId, clusterId, compositeComponentId);
      } else {
        NavigationActions.openContainerDetails(containerId);
      }
    },

    manageContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let containerId = this.getContainerId();
      ContainerActions.openManageContainers(containerId);
    },

    startContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.startContainer(this.getContainerId());
    },

    stopContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      ContainerActions.stopContainer(this.getContainerId());
    },

    removeContainer: function() {
      this.confirmRemoval(ContainerActions.removeContainer, [this.getContainerId()]);
    },

    scaleContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let compositionContextId = this.model.customProperties
                                  && this.model.customProperties.__composition_context_id;

      ContainerActions.scaleContainer(this.model.descriptionLink, compositionContextId);
    },

    showHost: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let queryOptions = {
        documentId: this.model.hostDocumentId
      };

      NavigationActions.openHosts(queryOptions);
    },

    showApp: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.APPLICATIONS,
        documentId: this.applicationId
      };

      NavigationActions.openContainers(queryOptions, true);
    },

    showNetworks: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.NETWORKS,
        $occurrence: constants.SEARCH_OCCURRENCE.ANY,
        any: this.networkIds
      };

      NavigationActions.openNetworks(queryOptions);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }
  }
});

Vue.component('container-grid-item', ContainersListItem);

export default ContainersListItem;
