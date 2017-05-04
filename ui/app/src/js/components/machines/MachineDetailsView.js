/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import MachineDetailsViewVue from 'components/machines/MachineDetailsViewVue.html';
import Component from 'components/common/Component';
import MaximizableBehaviour from 'components/common/MaximizableBehaviour'; //eslint-disable-line
import MachineStats from 'components/machines/MachineStats'; //eslint-disable-line

import { MachineActions } from 'actions/Actions';

import utils from 'core/utils';

Vue.component('machine-details', {
  template: MachineDetailsViewVue,
  data: function() {
    return {};
  },

  props: {
    model: { required: true }
  },

  computed: {
    hasOperationError() {
      return this.model.operationFailure && (this.model.operationFailure != null);
    },
    hasGeneralError() {
      return this.model.validationErrors && this.model.validationErrors._generic;
    },
    generalError() {
      return this.hasGeneralError ? this.model.validationErrors._generic : '';
    },

    endpointIconSrc() {
      return utils.getAdapter(this.model.instance.endpoint.endpointType).iconSrc;
    }
  },

  methods: {
    refresh: function() {
      MachineActions.refreshMachineDetails();
    },

    stateMessage(state) {
      return i18n.t('state.' + state);
    }
  }
});

class MachineDetails extends Component {
  constructor() {
    super();
    this.$el = $('<div>').append('<machine-details v-bind:model="currentModel">');
  }

  getEl() {
    return this.$el;
  }

  attached() {
    this.vue = new Vue({
      el: this.$el[0],
      data: {
        currentModel: {}
      }
    });
  }

  detached() {
    if (this.vue) {
      this.vue.$destroy();
      this.vue = null;
    }
  }

  setData(data) {
    Vue.set(this.vue, 'currentModel', data);
  }
}

export default MachineDetails;
