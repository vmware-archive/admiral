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

var VueTimeoutAlert = Vue.extend({
  template: `<div class="timeout-alert" v-show="show">{{text}}</div>`,
  props: {
    text: {
      required: true,
      type: String
    },
    show: {
      required: true,
      type: Boolean,
      default: true
    },
    time: {
      required: false,
      type: Number,
      default: 3000
    }
  },
  data: function() {
    return {
      shown: false
    };
  },
  attached: function() {
    this.unwatchShow = this.$watch('show', (show) => {
      if (show && !this.shown) {
        this.shown = true;
        clearTimeout(this.timeoutId);

        this.timeoutId = setTimeout(() => {
          this.show = false;
        }, this.time);
      } else {
        this.show = false;
      }
    });
  },
  detached: function() {
    this.unwatchShow();
  }
});

Vue.component('timeout-alert', VueTimeoutAlert);

export default VueTimeoutAlert;
