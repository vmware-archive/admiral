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

var VueTitleActionButton = Vue.extend({
  template: `<div class="title-action-button" v-show="show"><a class="btn btn-circle"
              title="{{tooltip}}" v-on:mousedown="spinIt()" v-on:click="notifyAction()"
                ><i class="fa fa-{{iconName}}"></i></a></div>`,
  props: {
    name: {
      required: true,
      type: String
    },
    iconName: {
      required: false,
      type: String,
      default: 'ship'
    },
    show: {
      required: false,
      type: Boolean,
      default: true
    },
    tooltip: {
      required: false,
      type: String
    },
    spinnable: {
      required: false,
      type: Boolean,
      default: true
    },
    confirmable: {
      required: false,
      type: Boolean,
      default: true
    },
    time: {
      required: false,
      type: Number,
      default: 1000
    },
    stopSpin: {
      required: false,
      type: Boolean,
      default: false
    }
  },
  attached: function() {
    this.unwatchStopSpin = this.$watch('stopSpin', (stopSpin) => {
      if (stopSpin) {
        this.spinStop();
      }
    });
  },
  detached: function() {
    this.unwatchStopSpin();
  },
  methods: {
    notifyAction: function() {
      if (this.confirmable) {
        this.$dispatch('title-action-confirm', {
          name: this.name,
          title: this.tooltip
        });
      } else {
        this.$dispatch('title-action', this.name);
      }
    },
    spinIt: function() {
      if (!this.spinnable) {

        $(this.$el).find('i').removeClass('fa-' + this.iconName);
        $(this.$el).find('i').addClass('fa-spinner');
        $(this.$el).find('i').addClass('fa-spin');

      } else {

        $(this.$el).find('i').addClass('fa-spin');

        clearTimeout(this.timeoutId);

        this.timeoutId = setTimeout(() => {
          this.spinStop();
        }, this.time);
      }
    },

    spinStop: function() {
      $(this.$el).find('i').removeClass('fa-spin');
    }
  }
});

Vue.component('title-action-button', VueTitleActionButton);

export default VueTitleActionButton;
