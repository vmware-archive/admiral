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

import AppComputeVue from 'components/AppComputeVue.html';
import AppMixin from 'components/AppMixin';
import HomeCompute from 'components/HomeCompute';//eslint-disable-line
import MachinesView from 'components/machines/MachinesView';//eslint-disable-line
import ComputeView from 'components/compute/ComputeView';//eslint-disable-line
import PlacementsView from 'components/placements/PlacementsView';//eslint-disable-line
import EnvironmentsView from 'components/profiles/EnvironmentsView';//eslint-disable-line
import EndpointsView from 'components/endpoints/EndpointsView';//eslint-disable-line
import computeConstants from 'core/computeConstants';

var AppComputeVueComponent = Vue.extend({
  template: AppComputeVue,
  mixins: [AppMixin],
  data: function() {
    return {
      computeConstants: computeConstants
    };
  }
});

Vue.component('app', AppComputeVueComponent);

function AppCompute($el) {
  var $selfEl = $('<app>').attr('v-bind:model', 'model');
  $el.append($selfEl);

  this.vue = new Vue({
    el: $el[0],
    data: {
      model: {
        centerView: {}
      }
    }
  });
}

AppCompute.prototype.setData = function(data) {
  Vue.set(this.vue, 'model', data);
};

export default AppCompute;
