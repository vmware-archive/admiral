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

import HomeVue from 'components/HomeVue.html';
import DocsHelpMixin from 'components/common/DocsHelpMixin';
import VueAdapter from 'components/common/VueAdapter';
import HostView from 'components/hosts/HostView'; //eslint-disable-line
import EasterEgg from 'components/EasterEgg'; //eslint-disable-line
import LoginPanel from 'components/LoginPanel'; //eslint-disable-line
import { NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var HomeVueComponent = Vue.extend({
  template: HomeVue,

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

  mixins: [DocsHelpMixin],

  computed: {
    isVic: function() {
      return utils.isVic();
    }
  },

  methods: {
    openAddHost: function() {
      NavigationActions.openHomeAddHost();
    },
    openHosts: function() {
      NavigationActions.openHosts();
    },
    goBack: function() {
      NavigationActions.openHome();
    }
  }
});

const TAG_NAME = 'home-view';
Vue.component(TAG_NAME, HomeVueComponent);

function Home($el) {
  return new VueAdapter($el, TAG_NAME);
}

export default Home;
