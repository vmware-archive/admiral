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

var VueCountdownOperationStart = Vue.extend({
  template: `<div class="countdownStart" v-bind:class="{'hide': !show}"><span><a
                href="#" v-on:click="cancelOperation($event)"
                >{{i18n('cancel')}}</a> {{textPendingOp}} {{counter}}</span><i
                  class="fa fa-spinner fa-spin"></i></div>`,
  props: {
    show: {
      required: true,
      type: Boolean,
      default: false
    },
    countFrom: {
      required: false,
      type: Number,
      default: 3
    },
    time: {
      required: false,
      type: Number,
      default: 1000
    },
    pendingOperationText: {
      required: false,
      type: String
    }
  },
  data: function() {
    return {
      counter: 0
    };
  },
  computed: {
    textPendingOp: function() {
      return this.pendingOperationText
                ? this.pendingOperationText
                : i18n.t('pendingOperationCancel');
    }
  },
  attached: function() {
    this.unwatchShow = this.$watch('show', (show) => {
      if (show) {
        this.startCountdown();
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchShow();
  },
  methods: {
    startCountdown: function() {
      clearInterval(this.interval);
      this.counter = this.countFrom;

      this.interval = setInterval(() => {
        this.decrementCounter();
      }, this.time);
    },

    decrementCounter: function() {
      this.counter--;
      if (this.counter === 0) {
        clearInterval(this.interval);

        this.$dispatch('countdown-operation-complete');
      }
    },

    cancelOperation: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      clearInterval(this.interval);
      this.counter = 0;

      this.$dispatch('countdown-operation-cancel');
    }
  }
});

Vue.component('countdown-operation-start', VueCountdownOperationStart);

export default VueCountdownOperationStart;
