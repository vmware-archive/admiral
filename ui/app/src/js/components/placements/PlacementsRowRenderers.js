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

import PlacementsRowTemplate from 'PlacementsRowTemplate';
import PlacementsRowHighlightTemplate from 'PlacementsRowHighlightTemplate';
import { NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var renderers = {
  render: function(placement) {
    let rowRendererEl = $(PlacementsRowTemplate(placement));

    rowRendererEl.find('.placementInstances').on('click', function(e) {
      e.preventDefault();
      return NavigationActions.showContainersPerPlacement(placement.documentSelfLink);
    });

    if (!utils.isApplicationEmbedded()) {
      rowRendererEl.find('td#deploymentPolicy').hide();
    }

    return rowRendererEl;
  },

  renderHighlighted: function(placement, $placementRow, isNew, isUpdated) {
    var placementHighlight = $.extend({}, placement);

    placementHighlight.placementRow = $placementRow.html();
    placementHighlight.isNew = isNew;
    placementHighlight.isUpdated = isUpdated;

    if (!utils.isApplicationEmbedded()) {
      placementHighlight.find('th.th-wide')[0].hide();
      placementHighlight.find('.highlight-item').prop('colspan', 8);
      placementHighlight.find('th.th-wide').css('width', '14%');
      placementHighlight.find('th.th-medium').css('width', '12%');
      placementHighlight.find('th.th-small').css('width', '10%');
    } else {
      placementHighlight.find('th.th-wide').css('width', '14%');
      placementHighlight.find('th.th-medium').css('width', '11%');
      placementHighlight.find('th.th-small').css('width', '9%');
    }

    return $(PlacementsRowHighlightTemplate(placementHighlight));
  }
};

export default renderers;
