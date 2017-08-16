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
import utils from 'core/utils';

export default Vue.component('compute-grid-item', {
  template: ComputeItemVue,
  props: {
    model: {required: true}
  },
  computed: {
    powerStateOn() {
      return this.model.powerState === constants.STATES.ON;
    },
    hostDisabled() {
      return this.model.powerState === constants.STATES.SUSPEND;
    },
    hostName() {
      return this.model.name;
    },
    epzNames() {
      return this.model.epzs.map((epz) => epz.epzName).join(', ');
    },
    computeType() {
      return (this.model.computeType || this.model.type).toLowerCase();
    },
    endpointIconSrc() {
      return utils.getAdapter(this.model.endpointType).iconSrc;
    }
  },
  methods: {
    stateMessage() {
      return utils.getUnifiedState(this.model);
    },
    percentageLevel(percentage) {
      if (percentage < 50) {
        return 'success';
      } else if (percentage < 80) {
        return 'warning';
      } else {
        return 'danger';
      }
    },
    editCompute(event) {
      event.preventDefault();

      NavigationActions.editCompute(this.model.selfLinkId);
    }
  }
});
