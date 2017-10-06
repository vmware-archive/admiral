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

import ContainerPropertiesVue from 'components/containers/ContainerPropertiesVue.html';
import { NavigationActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import ft from 'core/ft';

var ContainerProperties = Vue.extend({
  template: ContainerPropertiesVue,
  props: {
    model: { required: true }
  },
  computed: {
    isHostsViewLinksEnabled: function() {
      return this.model.hostName && this.model.hostDocumentId && ft.isHostsViewLinksEnabled();
    },
    portLinks: function() {
      return utils.getPortLinks(this.model.hostPublicAddress, this.model.ports);
    },
    applicationId: function() {
      return this.model.compositeComponentLink
        ? utils.getDocumentId(this.model.compositeComponentLink) : null;
    },
    networksInfo: function() {
      let result = [];

      if (this.model.networks) {
        Object.keys(this.model.networks).forEach(network => {
          result.push({
            name: network,
            ipv4_address: this.model.networks[network].ipv4_address
          });
        });
      }

      if (this.model.builtinNetworks) {
        Object.keys(this.model.builtinNetworks).forEach(network => {
          result.push({
            name: network,
            ipv4_address: this.model.builtinNetworks[network].ipv4_address
          });
        });
      }

      return result;
    },
    hasNetworksInfo: function() {
      return this.networksInfo.length > 0;
    }
  },
  methods: {
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

    showNetwork: function(networkName) {
      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.NETWORKS,
        any: networkName
      };

      NavigationActions.openNetworks(queryOptions);
    },

    isBuiltinNetwork: function(networkName) {
      return utils.isBuiltinNetwork(networkName);
    }

  }
});

Vue.component('container-properties', ContainerProperties);

export default ContainerProperties;
