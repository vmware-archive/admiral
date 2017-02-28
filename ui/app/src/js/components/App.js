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

import AppVue from 'components/AppVue.html';
import AppMixin from 'components/AppMixin';
import Home from 'components/Home';//eslint-disable-line
import Navigation from 'components/common/Navigation';//eslint-disable-line
import HostsView from 'components/hosts/HostsView';//eslint-disable-line
import TemplatesView from 'components/templates/TemplatesView';//eslint-disable-line
import PlacementsView from 'components/placements/PlacementsView';//eslint-disable-line
import ContainersView from 'components/containers/ContainersView';//eslint-disable-line
import NgView from 'components/ng/NgView';//eslint-disable-line

var AppVueComponent = Vue.extend({
  template: AppVue,
  mixins: [AppMixin]
});

Vue.component('app', AppVueComponent);

function App($el) {
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

App.prototype.setData = function(data) {
  Vue.set(this.vue, 'model', data);
};

export default App;
