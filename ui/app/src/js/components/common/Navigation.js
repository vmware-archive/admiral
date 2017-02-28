/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import NavigationVue from 'components/common/NavigationVue.html';
import constants from 'core/constants';
import computeConstants from 'core/computeConstants';
import ft from 'core/ft';

var getDefaultViewsByCategory = function(views) {
  var defaultViewByCategory = {};
  for (var v in views) {
    if (!views.hasOwnProperty(v)) {
      continue;
    }
    var view = views[v];
    var innerViews = view.VIEWS;
    if (innerViews) {
      for (var iv in innerViews) {
        if (!innerViews.hasOwnProperty(iv)) {
          continue;
        }
        var innerView = innerViews[iv];
        if (innerView.default) {
          defaultViewByCategory[view.name] = innerView.route;
        }
      }
    }
  }
  return defaultViewByCategory;
};

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
      lastActiveViewByCategory: getDefaultViewsByCategory(constants.VIEWS),
      computeLastActiveViewByCategory: getDefaultViewsByCategory(computeConstants.VIEWS)
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
            lastActiveViewByCategory[item.name] = activeItem.route;
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
    },
    areClosuresAllowed: function() {
      return ft.areClosuresAllowed();
    },
    isKubernetesEnabled: function() {
      return ft.isKubernetesHostOptionEnabled();
    },
    showProjectsInNavigation: function() {
      return ft.showProjectsInNavigation();
    }
  }
});

Vue.component('navigation', Navigation);

export default Navigation;
