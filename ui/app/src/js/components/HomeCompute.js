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

import HomeComputeVue from 'HomeComputeVue';
import VueAdapter from 'components/common/VueAdapter';
import HostView from 'components/hosts/HostView'; //eslint-disable-line
import LoginPanel from 'components/LoginPanel'; //eslint-disable-line
import { NavigationActions } from 'actions/Actions';

var HomeComputeVueComponent = Vue.extend({
  template: HomeComputeVue,

  props: {
    model: {
      required: true,
      type: Object,

      default: () => {
        return {
          contextView: {},
          hostAddView: null
        };
      }
    }
  },

  attached: function() {
    setTimeout(NavigationActions.openEnvironments, 4000);
  },

  methods: {
    open: function() {
      NavigationActions.openEnvironments();
    },
    goBack: function() {
      NavigationActions.openHome();
    }
  }
});

const TAG_NAME = 'home-compute-view';
Vue.component(TAG_NAME, HomeComputeVueComponent);

function HomeCompute($el) {
  return new VueAdapter($el, TAG_NAME);
}

export default HomeCompute;
