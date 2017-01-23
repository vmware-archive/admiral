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

var AlertItemMixin = {
  data: function() {
    return {
      alert: {
        type: 'warning',
        show: false,
        message: ''
      }
    };
  },
  methods: {
    showAlert: function(messageKey) {
      this.alert.message = i18n.t(messageKey);
      this.alert.show = true;
    },

    closeAlert: function() {
      this.alert.show = false;
      this.alert.message = '';
    }
  }
};

export default AlertItemMixin;
