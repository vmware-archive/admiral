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

import { ComputeActions, ComputeContextToolbarActions, NavigationActions } from 'actions/Actions';
import constants from 'core/constants';
import Tags from 'components/common/Tags';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import ComputeEditViewVue from 'components/compute/ComputeEditViewVue.html';

const placementZoneManageOptions = [{
  id: 'rp-create',
  name: i18n.t('app.placementZone.createNew'),
  icon: 'plus'
}, {
  id: 'rp-manage',
  name: i18n.t('app.placementZone.manage'),
  icon: 'pencil'
}];

// The Host View component
var ComputeEditView = Vue.extend({
  template: ComputeEditViewVue,

  props: {
    model: {
      required: true
    }
  },

  computed: {
    validationErrors: function() {
      return this.model.validationErrors || {};
    },
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    }
  },

  attached: function() {
    var elemPlacementZone = $(this.$el).find('#placementZone .form-control');
    this.placementZoneInput = new DropdownSearchMenu(elemPlacementZone, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.placementZone.entity')
      }),
      searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
        entity: i18n.t('app.placementZone.entity')
      })
    });

    this.placementZoneInput.setManageOptions(placementZoneManageOptions);
    this.placementZoneInput.setManageOptionSelectCallback((option) => {
      if (option.id === 'rp-create') {
        ComputeContextToolbarActions.createPlacementZone();
      } else {
        ComputeContextToolbarActions.managePlacementZones();
      }
    });

    this.placementZoneInput.setOptionSelectCallback((option) => {
      this.placementZone = option;
    });
    this.placementZoneInput.setClearOptionSelectCallback(() => {
      this.placementZone = undefined;
    });

    this.unwatchPlacementZones = this.$watch('model.placementZones', () => {
      if (this.model.placementZones === constants.LOADING) {
        this.placementZoneInput.setLoading(true);
      } else {
        this.placementZoneInput.setLoading(false);
        this.placementZoneInput.setOptions(
            (this.model.placementZones || []).map((config) => config.resourcePoolState));
      }
    }, {immediate: true});

    this.unwatchPlacementZone = this.$watch('model.placementZone',
                                           (placementZone, oldPlacementZone) => {
      if (this.model.placementZone && placementZone !== oldPlacementZone) {
        this.placementZoneInput.setSelectedOption(this.model.placementZone.resourcePoolState);
      }
    }, {immediate: true});

    this.tagsInput = new Tags($(this.$el).find('#tags .tags-input'));

    this.unwatchModel = this.$watch('model', (model, oldModel) => {
        oldModel = oldModel || {};
        if (model.placementZone !== oldModel.placementZone) {
          this.placementZoneInput.setSelectedOption(model.placementZone);
          this.placementZone = this.placementZoneInput.getSelectedOption();
        }
        if (model.tags !== oldModel.tags) {
          this.tagsInput.setValue(model.tags);
          this.tags = this.tagsInput.getValue();
        }
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchPlacementZones();
    this.unwatchPlacementZone();
    this.unwatchModel();
  },

  methods: {
    goBack: function() {
      NavigationActions.openCompute();
    },
    saveCompute: function() {
      let computeModel = {
        dto: this.model.dto,
        resourcePoolLink: this.placementZone ? this.placementZone.documentSelfLink : null,
        selfLinkId: this.model.selfLinkId
      };
      let tags = this.tagsInput.getValue();
      ComputeActions.updateCompute(computeModel, tags);
    },
    openToolbarPlacementZones: ComputeContextToolbarActions.openToolbarPlacementZones,
    closeToolbar: ComputeContextToolbarActions.closeToolbar
  }
});

Vue.component('compute-edit-view', ComputeEditView);

export default ComputeEditView;
