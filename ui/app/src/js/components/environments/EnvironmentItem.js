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

import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import EnvironmentItemVue from 'components/environments/EnvironmentItemVue.html';
import { EnvironmentsActions, NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var EnvironmentItem = Vue.extend({
  template: EnvironmentItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {
      required: true
    }
  },
  computed: {
    endpointName: function() {
      return (this.model.endpoint && this.model.endpoint.name) || '';
    },
    endpointType: function() {
      return (this.model.endpoint && this.model.endpoint.endpointType) || this.model.endpointType;
    }
  },
  attached: function() {
  },
  methods: {
    removeEnvironment: function() {
      this.confirmRemoval(EnvironmentsActions.deleteEnvironment, [this.model]);
    },
    editEnvironment: function(event) {
      event.preventDefault();

      NavigationActions.editEnvironment(utils.getDocumentId(this.model.documentSelfLink));
    }
  }
});

Vue.component('environment-grid-item', EnvironmentItem);

export default EnvironmentItem;
