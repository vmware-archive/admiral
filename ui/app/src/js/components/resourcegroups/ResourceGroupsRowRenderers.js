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

import ResourceGroupsRowTemplate from 'components/resourcegroups/ResourceGroupsRowTemplate.html';
import ResourceGroupsRowHighlightTemplate from
  'components/resourcegroups/ResourceGroupsRowHighlightTemplate.html';

var renderers = {
  render: function(groupHolder) {
    var $el = $(ResourceGroupsRowTemplate(groupHolder));

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

  renderHighlighted: function(groupHolder, groupHolderRow,
                              isNew, isUpdated, validationErrors) {
    var additionalProps = {
      isNew: isNew,
      isUpdated: isUpdated,
      validationErrors: validationErrors,
      resourceGroupsRow: groupHolderRow.html()
    };

    var model = $.extend({}, groupHolder, additionalProps);

    return $(ResourceGroupsRowHighlightTemplate(model));
  }
};
export default renderers;
