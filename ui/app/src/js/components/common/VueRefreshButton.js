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

var VueRefreshButton = Vue.extend({
  template: `<div class="refresh-button" v-show="supported"><a class="btn btn-circle-outline"
              title="{{title}}" v-on:mousedown="spinIt()"
                ><i class="fa fa-{{iconName}}"></i></a></div>`,
  props: {
    iconName: {
      required: false,
      type: String,
      default: 'refresh'
    },
    supported: {
      required: false,
      type: Boolean,
      default: true
    },
    tooltip: {
      required: false,
      type: String
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
  computed: {
    title: function() {
      return this.tooltip ? this.tooltip : i18n.t('refresh');
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
    spinIt: function() {
      $(this.$el).find('i').addClass('fa-spin');

      clearTimeout(this.timeoutId);

      this.timeoutId = setTimeout(() => {
        this.spinStop();
      }, this.time);
    },

    spinStop: function() {
      $(this.$el).find('i').removeClass('fa-spin');
    }
  }
});

Vue.component('refresh-button', VueRefreshButton);

export default VueRefreshButton;
