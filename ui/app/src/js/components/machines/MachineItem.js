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

import MachineItemVue from 'components/machines/MachineItemVue.html';
import constants from 'core/constants';
import utils from 'core/utils';

var MachineItem = Vue.extend({
  template: MachineItemVue,
  props: {
    model: {required: true}
  },
  computed: {
    powerStateOn: function() {
      return this.model.powerState === constants.STATES.ON;
    },
    hostDisabled: function() {
      return this.model.powerState === constants.STATES.SUSPEND;
    },
    hostName: function() {
      return this.model.name;
    }
  },
  attached: function() {
  },
  methods: {
    stateMessage: function(state) {
      return i18n.t('state.' + state);
    },

    percentageLevel: function(percentage) {
      if (percentage < 50) {
        return 'success';
      } else if (percentage < 80) {
        return 'warning';
      } else {
        return 'danger';
      }
    },

    extractId: function(hostId) {
      return utils.extractHostId(hostId);
    }
  }
});

Vue.component('machine-grid-item', MachineItem);

export default MachineItem;
