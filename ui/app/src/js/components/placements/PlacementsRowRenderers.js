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

import PlacementsRowTemplate from 'components/placements/PlacementsRowTemplate.html';
import PlacementsRowHighlightTemplate from
  'components/placements/PlacementsRowHighlightTemplate.html';
import { NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

var renderers = {
  render: function(placement) {
    let rowRendererEl = $(PlacementsRowTemplate(placement));

    rowRendererEl.find('.placementInstances').on('click', function(e) {
      e.preventDefault();
      if (utils.isApplicationCompute()) {
        return NavigationActions.showMachinesPerPlacement(placement.documentSelfLink);
      } else {
        return NavigationActions.showContainersPerPlacement(placement.documentSelfLink);
      }
    });

    return rowRendererEl;
  },

  renderHighlighted: function(placement, $placementRow, isNew, isUpdated) {
    var placementHighlight = $.extend({}, placement);

    placementHighlight.placementRow = $placementRow.html();
    placementHighlight.isNew = isNew;
    placementHighlight.isUpdated = isUpdated;

    return $(PlacementsRowHighlightTemplate(placementHighlight));
  }
};

export default renderers;
