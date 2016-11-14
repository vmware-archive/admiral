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

    let highlightTemplate = $(PlacementsRowHighlightTemplate(placementHighlight));

    if (!utils.isApplicationEmbedded()) {
      highlightTemplate.find('.highlight-item').prop('colspan', 8);
      highlightTemplate.find('th.th-wide#deployment-policy').hide();
      highlightTemplate.find('th.th-wide').css('width', '15%');
      highlightTemplate.find('th.th-medium').css('width', '13%');
      highlightTemplate.find('th.th-small').css('width', '12%');
    } else {
      highlightTemplate.find('th.th-wide').css('width', '14%');
      highlightTemplate.find('th.th-medium').css('width', '11%');
      highlightTemplate.find('th.th-small').css('width', '9%');
    }

    return highlightTemplate;
  }
};

export default renderers;
