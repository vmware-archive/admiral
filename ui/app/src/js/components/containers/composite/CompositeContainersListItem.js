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

import CompositeContainersListItemVue from 'CompositeContainersListItemVue'; //eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions'; //eslint-disable-line

var CompositeContainersListItem = Vue.extend({
  template: CompositeContainersListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },
  computed: {
    showNumbers: function() {
      return (typeof this.model.componentLinks !== 'undefined');
    },
    servicesCount: function() {
      if (!this.showNumbers) {
        return 'N/A';
      }

      var containerTypesCount = {};

      /*
      Service clusters cannot be reliably calculated using plain componentLinks

      var containers = this.model.componentLinks;
      containers.forEach(function(container) {
        var type = container.descriptionLink;
        var num = containerTypesCount[type] || 0;
        containerTypesCount[type] = num + 1;
      });
      */

      return Object.keys(containerTypesCount).length;
    }
  },
  methods: {
    containerStatusDisplay: utils.containerStatusDisplay,
    openContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let compositeId = utils.getDocumentId(this.model.documentSelfLink);
      NavigationActions.openCompositeContainerDetails(compositeId);
    },

    startContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let compositeId = utils.getDocumentId(this.model.documentSelfLink);
      ContainerActions.startCompositeContainer(compositeId);
    },

    stopContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let compositeId = utils.getDocumentId(this.model.documentSelfLink);
      ContainerActions.stopCompositeContainer(compositeId);
    },

    removeCompositeContainer: function() {
      let compositeId = utils.getDocumentId(this.model.documentSelfLink);

      this.confirmRemoval(ContainerActions.removeCompositeContainer, [compositeId]);
    },
    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }
  }
});

Vue.component('composite-container-grid-item', CompositeContainersListItem);

export default CompositeContainersListItem;
