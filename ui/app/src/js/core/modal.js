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

var $lastModalEl;

var modal = {

  show: function($el) {
    if ($lastModalEl) {
      this.hide();
    }

    $('body').append($el);
    $el.modal({
      'show': true,
      'backdrop': 'static',
      'keyboard': false
    });
    $lastModalEl = $el;

  },

  hide: function() {
    if ($lastModalEl) {
      var $elementToRemove = $lastModalEl;
      $lastModalEl.on('hidden.bs.modal', function() {
        $elementToRemove.remove();
      });

      $lastModalEl.modal('hide');
      $lastModalEl = null;
    }
  }
};

export default modal;
