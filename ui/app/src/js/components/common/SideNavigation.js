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

import SideNavigationVue from 'components/common/SideNavigationVue.html';

import constants from 'core/constants';
import computeConstants from 'core/computeConstants';
import ft from 'core/ft';

export default Vue.component('sidenav', {
  template: SideNavigationVue,

  props: {
    model: {
      required: false
    },
    application: {
      required: true,
      type: String,
      default: 'admiral'
    }
  },

  computed: {
    currentView: function() {
      return this.model && this.model.currentView;
    }
  },

  data: function() {
    return {
      constants: constants,
      computeConstants: computeConstants
    };
  },

  methods: {

    isFeatureEnabled: function(feature) {
      if (feature === 'closures') {

        return ft.areClosuresAllowed();
      } else if (feature === 'k8s') {

        return ft.isKubernetesHostOptionEnabled();
      } else if (feature === 'projects') {

        return ft.showProjectsInNavigation();
      }

      return false;
    }
  }
});
