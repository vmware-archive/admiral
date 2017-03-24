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
import { PlacementActions, PlacementContextToolbarActions } from 'actions/Actions';
import utils from 'core/utils';
import ft from 'core/ft';

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
    },
    isStandaloneMode: function() {
      return !utils.isApplicationEmbedded();
    },
    showProjectsToolbarItem: function() {
      return this.isStandaloneMode && !ft.showProjectsInNavigation();
    }
  },
  attached: function() {
    var $placementsListHolder = $(this.$el).find('.list-holder');
    this.placementsList = new InlineEditableList($placementsListHolder, PlacementsListTemplate,
                                             PlacementsRowRenderers);

    this.placementsList.setRowEditor(PlacementsRowEditor);
    this.placementsList.setDeleteCallback(PlacementActions.deletePlacement);
    this.placementsList.setEditCallback(PlacementActions.editPlacement);

    this.unwatchModel = this.$watch('model.placements', (placements) => {
      this.placementsList.setData(placements);
    });
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    openToolbarPlacementZones: PlacementContextToolbarActions.openToolbarPlacementZones,
    openToolbarResourceGroups: PlacementContextToolbarActions.openToolbarResourceGroups,
    closeToolbar: PlacementContextToolbarActions.closeToolbar,
    refresh: PlacementActions.openPlacements
  }
});

Vue.component('placements-view', PlacementsView);

export default PlacementsView;
