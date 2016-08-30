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

var ActionConfirmationSupportMixin = {
  data: function() {
    return {
      confirmationOperation: null
    };
  },
  methods: {
    askConfirmation: function($event, actionName) {
      $event.preventDefault();
      $event.stopPropagation();

      this.confirmationOperation = actionName;
    },
    handleConfirmation: function(actionName) {
      // provide implementation
      console.log('Action confirmed: ' + actionName);
    }
  },
  events: {
    'action-confirmed': function(actionName) {
      this.confirmationOperation = null;

      this.handleConfirmation(actionName);
    },
    'action-cancelled': function() {
      this.confirmationOperation = null;
    }
  }
};

export default ActionConfirmationSupportMixin;
