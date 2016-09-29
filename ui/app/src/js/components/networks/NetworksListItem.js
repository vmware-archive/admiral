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

import NetworksListItemVue from 'NetworksListItemVue'; //eslint-disable-line
import { NavigationActions } from 'actions/Actions';

var NetworksListItem = Vue.extend({
  template: NetworksListItemVue,
  props: {
    model: {required: true}
  },

  computed: {

    hasConnectedContainers: function() {
      return this.model.containers && this.model.containers.length > 0;
    },

    connectedContainersCount: function() {
      return this.hasConnectedContainers ? this.model.containers.length : 0;
    },

    connectedContainersDocumentIds: function() {
      return !this.hasConnectedContainers ? [] : this.model.containers.map(
        (container) => {
          return container.documentId;
        });
    }

  },
  methods: {
    openConnectedContainers: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      if (this.hasConnectedContainers) {
        let queryOptions = {
          network: this.model.name
        };

        NavigationActions.openContainers(queryOptions);
      }
    }
  }
});

Vue.component('network-grid-item', NetworksListItem);

export default NetworksListItem;
