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

var DeleteConfirmationSupportMixin = {
  data: function() {
    return {
      showDeleteConfirmation: false
    };
  },
  methods: {
    askConfirmation: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.showDeleteConfirmation = true;
    },

    confirmRemoval: function(removalFn, params) {
      removalFn.apply(this, params);
    },

    cancelRemoval: function() {
      this.showDeleteConfirmation = false;
    }
  }
};

export default DeleteConfirmationSupportMixin;
