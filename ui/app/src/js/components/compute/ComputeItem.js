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

import ComputeItemVue from 'components/compute/ComputeItemVue.html';
import { NavigationActions } from 'actions/Actions';
import constants from 'core/constants';

var ComputeItem = Vue.extend({
  template: ComputeItemVue,
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
    },
    epzNames: function() {
      return this.model.epzs.map((epz) => epz.epzName).join(', ');
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

    editCompute: function(event) {
      event.preventDefault();

      NavigationActions.editCompute(this.model.selfLinkId);
    }
  }
});

Vue.component('compute-grid-item', ComputeItem);

export default ComputeItem;
