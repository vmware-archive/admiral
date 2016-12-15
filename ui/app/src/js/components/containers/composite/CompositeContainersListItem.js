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

import CompositeContainersListItemVue from
  'components/containers/composite/CompositeContainersListItemVue.html';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import utils from 'core/utils';
import links from 'core/links';
import constants from 'core/constants';
import { ContainerActions, NavigationActions } from 'actions/Actions'; //eslint-disable-line

var CompositeContainersListItem = Vue.extend({
  template: CompositeContainersListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true},
    showAlertManagedByCatalog: {required: true}
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
    showNumbers: function() {
      return (typeof this.model.componentLinks !== 'undefined');
    },
    hostsCount: function() {
      return this.model.hostLinks && this.model.hostLinks.length;
    },
    containersCount: function() {
      return this.model.componentLinks.filter(cl => cl.indexOf(links.CONTAINERS) === 0).length;
    },
    networkIds: function() {
      let networkLinks = this.model.componentLinks.filter(cl => cl.indexOf(links.NETWORKS) === 0);

      return networkLinks && networkLinks.map(link => utils.getDocumentId(link));
    },
    networksCount: function() {
      return this.networkIds && this.networkIds.length;
    },
    volumesCount: function() {
      return this.model.componentLinks.filter(
        cl => cl.indexOf(links.CONTAINER_VOLUMES) === 0).length;
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
    },
    numberOfIcons: function() {
      return Math.min(this.model.icons && this.model.icons.length, 4);
    }
  },
  attached: function() {
    this.$dispatch('attached', this);

    this.unwatchShowAlertManagedByCatalog = this.$watch('showAlertManagedByCatalog', () => {
      if (this.showAlertManagedByCatalog) {
        this.showManagedByCatalogAlert();
      }
    });
  },

  detached: function() {
    this.unwatchShowAlertManagedByCatalog();
  },
  methods: {
    containerStatusDisplay: utils.containerStatusDisplay,
    openContainer: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let compositeId = utils.getDocumentId(this.model.documentSelfLink);
      NavigationActions.openCompositeContainerDetails(compositeId);
    },

    manageComposite: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let catalogResourceId = utils.getDocumentId(this.model.documentSelfLink);
      ContainerActions.openManageComposite(catalogResourceId);
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
    },

    showHosts: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let hostIds = this.model.hostLinks.map((parentLink) => utils.getDocumentId(parentLink));
      let queryOptions = {
        $occurrence: constants.SEARCH_OCCURRENCE.ANY,
        any: hostIds
      };

      NavigationActions.openHosts(queryOptions);
    },

    showContainers: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS,
        any: utils.getDocumentId(this.model.documentSelfLink)
      };

      NavigationActions.openContainers(queryOptions);
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

    showManagedByCatalogAlert: function() {
      this.alert.message =
        i18n.t('app.resource.list.container.operations.errors.managedByCatalog');
      this.alert.show = true;
    },

    alertClosed: function() {
      this.alert.show = false;
      this.alert.message = '';
    }
  }
});

Vue.component('composite-container-grid-item', CompositeContainersListItem);

export default CompositeContainersListItem;
