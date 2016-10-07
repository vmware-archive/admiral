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

import NavigationVue from 'NavigationVue';
import constants from 'core/constants';//eslint-disable-line
import computeConstants from 'core/computeConstants';//eslint-disable-line
import utils from 'core/utils';//eslint-disable-line

var Navigation = Vue.extend({
  template: NavigationVue,

  props: {
    model: {
      required: false
    },
    app: {
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
      computeConstants: computeConstants,
      lastActiveViewByCategory: {
        [constants.VIEWS.RESOURCES.name]: 'containers'
      },
      computeLastActiveViewByCategory: {
        [computeConstants.VIEWS.RESOURCES.name]: 'machines'
      }
    };
  },

  attached: function() {

    this.unwatchCurrentView = this.$watch('currentView', (currentView) => {
      let consts = (this.app === 'admiral') ? constants : computeConstants;
      let lastActiveViewByCategory = (this.app === 'admiral') ?
        this.lastActiveViewByCategory : this.computeLastActiveViewByCategory;

      Object.values(consts.VIEWS).forEach((item) => {
        if (item.VIEWS) { // categories
          let activeItem;
          if (item.name === currentView) {
            activeItem = item;
          } else {
            activeItem = Object.values(item.VIEWS).find((subView) => {
              return (subView.name === currentView);
            });
          }

          if (activeItem) {
            lastActiveViewByCategory[item.name] = activeItem.name;
          }
        }
      });
    });
  },

  detached: function() {
    this.unwatchCurrentView();
  },

  methods: {
    isExpanded: function(item) {
      if (item.VIEWS) {
        if (item.name === this.currentView) {
            return true;
          } else {
            var activeItem = Object.values(item.VIEWS).find((subView) => {
              return (subView.name === this.currentView);
            });
            if (activeItem) {
              return true;
            }
          }
      }
      return false;
    },
    isCollapsed: function(item) {
      return !this.isExpanded(item);
    }
  }
});

Vue.component('navigation', Navigation);

export default Navigation;
