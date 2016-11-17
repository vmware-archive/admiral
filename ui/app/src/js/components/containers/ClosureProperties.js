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

import ClosurePropertiesVue from 'components/containers/ClosurePropertiesVue.html';
import { NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var ClosureProperties = Vue.extend({
  template: ClosurePropertiesVue,
  props: {
    model: { required: true }
  },
  computed: {
    closureId: function() {
      return utils.getDocumentId(this.model.documentSelfLink);
    },
    inputs: function() {
      return JSON.stringify(this.model.inputs);
    },
    outputs: function() {
      return JSON.stringify(this.model.outputs);
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

Vue.component('closure-properties', ClosureProperties);

export default ClosureProperties;
