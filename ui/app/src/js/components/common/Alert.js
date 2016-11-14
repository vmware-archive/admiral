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

import AlertTemplate from 'components/common/AlertTemplate.html';
import constants from 'core/constants';

function Alert($parentEl, $elSiblingToInsertTo, isInsertAfter) {
  var _this = this;
  var $alert = $(AlertTemplate());

  if (!isInsertAfter) {
    $alert.insertBefore($elSiblingToInsertTo);
  } else {
    $alert.insertAfter($elSiblingToInsertTo);
  }

  $alert.find('.close').click(function(e) {
    e.preventDefault();

    _this.toggle($parentEl);
  });
}

Alert.prototype.toggle = function($parentEl, alertType, alertMessage) {
  var $alertHolder = $parentEl.find('.alert-holder');
  var $alert = $alertHolder.find('.alert');

  if (alertMessage) {
    if (alertType) { // @see constants.ALERTS.TYPE
      this.clearType($alert);

      $alert.addClass('alert-' + alertType);
    }

    $alert.find('.alertMsg').html(alertMessage);

    $alertHolder.removeClass('alert-hidden');
  } else {
    $alertHolder.addClass('alert-hidden');

    // remove all alert types attached
    this.clearType($alert);

    $alert.find('.alertMsg').html('');
  }
};

Alert.prototype.clearType = function($alert) {

  // remove previous alert types attached
  $alert.removeClass('alert-' + constants.ALERTS.TYPE.FAIL)
    .removeClass('alert-' + constants.ALERTS.TYPE.WARNING)
    .removeClass('alert-' + constants.ALERTS.TYPE.SUCCESS);

};

export default Alert;
