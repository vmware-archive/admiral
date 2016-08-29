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

import CertificatesRowTemplate from 'CertificatesRowTemplate';
import CertificatesRowHighlightTemplate from 'CertificatesRowHighlightTemplate';

var renderers = {
  render: function(certificateHolder) {
    var $el = $(CertificatesRowTemplate(certificateHolder));

    $el.find('.item-expand').on('click', function(e) {
      e.preventDefault();
      var $item = $(e.currentTarget).closest('.item');
      $item.addClass('active');
    });

    $el.find('.item-collapse').on('click', function(e) {
      e.preventDefault();
      var $item = $(e.currentTarget).closest('.item');
      $item.removeClass('active');
    });

    return $el;
  },

  renderHighlighted: function(certificateHolder, $certificatesRow, isNew, isUpdated) {
    var additionalProps = {
      isNew: isNew,
      isUpdated: isUpdated
    };

    var model = $.extend({}, certificateHolder, additionalProps);

    return $(CertificatesRowHighlightTemplate(model));
  }
};
export default renderers;
