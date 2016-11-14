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

const resourcePoolManageOptions = [{
  id: 'rp-create',
  name: i18n.t('app.resourcePool.createNew'),
  icon: 'plus'
}, {
  id: 'rp-manage',
  name: i18n.t('app.resourcePool.manage'),
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
    var elemResourcePool = $(this.$el).find('#resourcePool .form-control');
    this.resourcePoolInput = new DropdownSearchMenu(elemResourcePool, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.resourcePool.entity')
      }),
      searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
        entity: i18n.t('app.resourcePool.entity')
      })
    });

    this.resourcePoolInput.setManageOptions(resourcePoolManageOptions);
    this.resourcePoolInput.setManageOptionSelectCallback((option) => {
      if (option.id === 'rp-create') {
        ComputeContextToolbarActions.createResourcePool();
      } else {
        ComputeContextToolbarActions.manageResourcePools();
      }
    });

    this.resourcePoolInput.setOptionSelectCallback((option) => {
      this.resourcePool = option;
    });
    this.resourcePoolInput.setClearOptionSelectCallback(() => {
      this.resourcePool = undefined;
    });

    this.unwatchResourcePools = this.$watch('model.resourcePools', () => {
      if (this.model.resourcePools === constants.LOADING) {
        this.resourcePoolInput.setLoading(true);
      } else {
        this.resourcePoolInput.setLoading(false);
        this.resourcePoolInput.setOptions(
            (this.model.resourcePools || []).map((config) => config.resourcePoolState));
      }
    }, {immediate: true});

    this.unwatchResourcePool = this.$watch('model.resourcePool',
                                           (resourcePool, oldResourcePool) => {
      if (this.model.resourcePool && resourcePool !== oldResourcePool) {
        this.resourcePoolInput.setSelectedOption(this.model.resourcePool.resourcePoolState);
      }
    }, {immediate: true});

    this.tagsInput = new Tags($(this.$el).find('#tags .tags-input'));

    this.unwatchModel = this.$watch('model', (model, oldModel) => {
        oldModel = oldModel || {};
        if (model.resourcePool !== oldModel.resourcePool) {
          this.resourcePoolInput.setSelectedOption(model.resourcePool);
          this.resourcePool = this.resourcePoolInput.getSelectedOption();
        }
        if (model.tags !== oldModel.tags) {
          this.tagsInput.setValue(model.tags);
          this.tags = this.tagsInput.getValue();
        }
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchResourcePools();
    this.unwatchResourcePool();
    this.unwatchModel();
  },

  methods: {
    goBack: function() {
      NavigationActions.openCompute();
    },
    saveCompute: function() {
      let computeModel = {
        dto: this.model.dto,
        resourcePoolLink: this.resourcePool ? this.resourcePool.documentSelfLink : null,
        selfLinkId: this.model.selfLinkId
      };
      let tags = this.tagsInput.getValue();
      ComputeActions.updateCompute(computeModel, tags);
    },
    openToolbarResourcePools: ComputeContextToolbarActions.openToolbarResourcePools,
    closeToolbar: ComputeContextToolbarActions.closeToolbar
  }
});

Vue.component('compute-edit-view', ComputeEditView);

export default ComputeEditView;
