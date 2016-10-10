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

var VueAlert = Vue.extend({
  template: `
    <div class="alert-holder" v-show="alertVisible" transition="fade">
      <div class="alert alert-{{alertType}}
      {{alertDismissible ? 'alert-dismissible' : ''}}"
      role="alert"><a class="close" v-if="alertDismissible">
      <i class="fa fa-close"></i></a>
      <i class="fa fa-exclamation-circle alert-icon"></i>
      <span>{{alertMessage}}</span></div></div>`,
  props: {
    alertType: {
      required: true,
      type: String
    },
    showAlert: {
      required: true
    },
    alertDismissible: {
      required: false,
      type: Boolean,
      default: true
    },
    alertMessage: {
      type: String
    },
    alertTimeout: {
      required: false,
      type: Number
    }
  },
  data: function() {
    return {
      alertVisible: false
    };
  },
  attached: function() {
    this.unwatchShowAlert = this.$watch('showAlert', (showAlert) => {
      this.toggleVisibility(showAlert);

      if (showAlert) {

        if (this.alertTimeout && this.alertTimeout > 0) {
          clearTimeout(this.timeoutId);

          this.timeoutId = setTimeout(() => {
            this.closeAlert();
          }, this.alertTimeout);
        }

      }

    }, {immediate: true});


    if (this.alertDismissible) {
      $(this.$el).find('.close').click(() => {
        this.closeAlert();
      });
    }
  },
  detached: function() {
    this.unwatchShowAlert();
  },
  methods: {
    closeAlert: function() {
      this.toggleVisibility(false);
      this.$dispatch('alert-closed');
    },

    toggleVisibility: function(show) {
      this.alertVisible = show;
    }
  }
});

Vue.component('alert', VueAlert);

export default VueAlert;
