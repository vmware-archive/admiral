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

import ContainersListItemVue from
  'components/containers/ContainersListItemVue.html';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var ContainersListItem = Vue.extend({
  template: ContainersListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },
  computed: {
    portsDisplayTexts: function() {
      return utils.getPortsDisplayTexts(this.model.hostAddress, this.model.ports);
    }
  },
  attached: function() {
    this.$dispatch('attached', this);
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
        any: this.model.hostDocumentId
      };

      NavigationActions.openHosts(queryOptions);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }
  }
});

Vue.component('container-grid-item', ContainersListItem);

export default ContainersListItem;
