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
    <div class="alert-holder alert-hidden">
      <div class="alert alert-{{alertType}} alert-dismissible fade in"
      role="alert"><a class="close"><i class="fa fa-close"></i></a>
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
    alertMessage: {
      type: String
    }
  },
  attached: function() {
    this.unwatchShowAlert = this.$watch('showAlert', (showAlert) => {
      this.toggleVisibility(showAlert);
    });

    var _this = this;
    $(this.$el).find('.close').click(function() {
      _this.toggleVisibility(false);
    });
  },
  detached: function() {
    this.unwatchShowAlert();
  },
  methods: {
    toggleVisibility: function(show) {
      if (show) {
        $(this.$el).removeClass('alert-hidden');
      } else {
        $(this.$el).addClass('alert-hidden');
      }
    }
  }
});

Vue.component('alert', VueAlert);

export default VueAlert;
