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

import InlineEditableList from 'components/common/InlineEditableList';
import PlacementsViewVue from 'components/placements/PlacementsViewVue.html';
import PlacementsListTemplate from 'components/placements/PlacementsListTemplate.html';
import PlacementsRowRenderers from 'components/placements/PlacementsRowRenderers';
import PlacementsRowEditor from 'components/placements/PlacementsRowEditor';
import PlacementZonesView from 'components/placementzones/PlacementZonesView'; //eslint-disable-line
import ResourceGroupsList from 'components/resourcegroups/ResourceGroupsList'; //eslint-disable-line
import DeploymentPoliciesList from 'components/deploymentpolicies/DeploymentPoliciesList'; //eslint-disable-line
import { PlacementActions, PlacementContextToolbarActions } from 'actions/Actions';
import utils from 'core/utils';

var PlacementsView = Vue.extend({
  template: PlacementsViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          placements: {},
          contextView: {}
        };
      }
    }
  },
  computed: {
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    innerContextExpanded: function() {
      var activeItemData = this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.data;
      return activeItemData && activeItemData.contextView && activeItemData.contextView.expanded;
    },
    hasError: function() {
      return this.model.error && this.model.error._generic;
    },
    errorMessage: function() {
      return this.hasError ? this.model.error._generic : '';
    },
    itemsCount: function() {
      return this.model.placements && this.model.placements.items.length;
    }
  },
  attached: function() {
    var $placementsListHolder = $(this.$el).find('.list-holder');
    this.placementsList = new InlineEditableList($placementsListHolder, PlacementsListTemplate,
                                             PlacementsRowRenderers);

    this.placementsList.setRowEditor(PlacementsRowEditor);
    this.placementsList.setDeleteCallback(PlacementActions.deletePlacement);
    this.placementsList.setEditCallback(PlacementActions.editPlacement);

    if (this.isStandaloneMode()) {
      $('th#deploymentPolicy').hide();
      $('th.th-wide').css('width', '15%');
      $('th.th-medium').css('width', '13%');
      $('th.th-small').css('width', '12%');
    } else {
      $('th.th-wide').css('width', '14%');
      $('th.th-medium').css('width', '11%');
      $('th.th-small').css('width', '9%');
    }

    this.unwatchModel = this.$watch('model.placements', (placements) => {
      this.placementsList.setData(placements);
    });
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    i18n: i18n.t,
    openToolbarPlacementZones: PlacementContextToolbarActions.openToolbarPlacementZones,
    openToolbarResourceGroups: PlacementContextToolbarActions.openToolbarResourceGroups,
    openToolbarDeploymentPolicies: PlacementContextToolbarActions.openToolbarDeploymentPolicies,
    closeToolbar: PlacementContextToolbarActions.closeToolbar,
    refresh: PlacementActions.openPlacements,
    isStandaloneMode: function() {
      return !utils.isApplicationEmbedded();
    }
  }
});

Vue.component('placements-view', PlacementsView);

export default PlacementsView;
