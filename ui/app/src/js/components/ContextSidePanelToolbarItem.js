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

var ContextSidePanelToolbarItem = Vue.extend({
  template: `
    <div class="toolbar-item" v-bind:class="{active: active}">
      <a href="#" class="btn btn-default" v-on:click="handleClick($event)">
        <i class="toolbar-item-icon" v-bind:class="iconClass"></i>
        <div v-show="showNotifications" transition="fade" class="toolbar-item-notification">
          {{notifications}}
        </div>
        <div class="toolbar-item-title">{{label}}</div>
      </a>
    </div>
  `,

  props: {
    active: {type: Boolean},
    iconClass: {type: String},
    label: {type: String},
    notifications: {
      required: false,
      type: Number,
      default: null
    }
  },

  computed: {
    showNotifications: function() {
      return Number.isInteger(this.notifications) && this.notifications > 0;
    }
  },

  methods: {
    handleClick: function($event) {
      $event.preventDefault();
      if (this.active) {
        this.$dispatch('close');
      } else {
        this.$dispatch('open');
      }
    }
  }
});

Vue.component('context-sidepanel-toolbar-item', ContextSidePanelToolbarItem);

export default ContextSidePanelToolbarItem;
