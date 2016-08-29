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

import ContainerPropertiesVue from 'ContainerPropertiesVue';
import { NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var ContainerProperties = Vue.extend({
  template: ContainerPropertiesVue,
  props: {
    model: { required: true }
  },
  computed: {
    portLinks: function() {
      return utils.getPortLinks(this.model.hostAddress, this.model.ports);
    }
  },
  methods: {
    showHost: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let queryOptions = {
        any: this.model.hostDocumentId
      };

      NavigationActions.openHosts(queryOptions);
    }
  }
});

Vue.component('container-properties', ContainerProperties);

export default ContainerProperties;
