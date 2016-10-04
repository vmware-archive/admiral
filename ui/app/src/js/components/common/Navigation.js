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
import utils from 'core/utils';//eslint-disable-line

var Navigation = Vue.extend({
  template: NavigationVue,

  props: {
    model: {
      required: false
    }
  },

  data: function() {
    return {
      constants: constants
    };
  },

  attached: function() {

    this.unwatchCurrentView = this.$watch('model.currentView', (currentView) => {

      Object.values(constants.VIEWS).forEach((item) => {
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
            this.expandCategory(item);
          } else {
            this.collapseCategory(item);
          }
        }
      });
    });
  },

  detached: function() {
    this.unwatchCurrentView();
  },

  methods: {
    findItemElement: function(item) {
      let elParent = $(this.$el).parent('.side-view');
      let elNavBar = $(elParent).find('.nav-bar');

      return $(elNavBar).find('.' + item.name);
    },
    isItemExpanded: function(item) {
      let elItem = this.findItemElement(item);

      return $(elItem).hasClass('expanded');
    },
    isExpanded: function(item) {
      if (item.VIEWS) {
       return this.isItemExpanded(item);
      }
    },
    isCollapsed: function(item) {
      return !this.isExpanded(item);
    },
    expandCategory: function(categoryItem) {
      let categoryEl = this.findItemElement(categoryItem);

      if (!$(categoryEl).hasClass('expanded')) {
        $(categoryEl).addClass('expanded');
        $(categoryEl).find('i').removeClass('fa-chevron-right');
        $(categoryEl).find('i').addClass('fa-chevron-down');
      }

      this.showAllSubItems(categoryItem);
    },
    collapseCategory: function(categoryItem) {
      let categoryEl = this.findItemElement(categoryItem);

      if ($(categoryEl).hasClass('expanded')) {
        $(categoryEl).removeClass('expanded');
        $(categoryEl).find('i').removeClass('fa-chevron-down');
        $(categoryEl).find('i').addClass('fa-chevron-right');
      }

      this.hideAllSubItems(categoryItem);
    },
    showAllSubItems: function(categoryItem) {
      Object.values(categoryItem.VIEWS).forEach((subItem) => {

        let elSubItem = this.findItemElement(subItem);
        if ($(elSubItem).hasClass('hide')) {
          $(elSubItem).removeClass('hide');
        }
      });
    },
    hideAllSubItems: function(categoryItem) {
      Object.values(categoryItem.VIEWS).forEach((subItem) => {

        let elSubItem = this.findItemElement(subItem);
        if (!$(elSubItem).hasClass('hide')) {
          $(elSubItem).addClass('hide');
        }
      });
    },
    handleCategoryClick: function($event, item) {
      $event.stopPropagation();

      if (!this.isExpanded(item)) {
        this.expandCategory(item);
      }
    }
  }
});

Vue.component('navigation', Navigation);

export default Navigation;
