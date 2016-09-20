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

import ComputeViewVue from 'ComputeViewVue';
import ComputeItem from 'components/compute/ComputeItem'; //eslint-disable-line
import ComputeDetails from 'components/compute/ComputeDetails'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { ComputeActions, NavigationActions } from 'actions/Actions';

var ComputeViewVueComponent = Vue.extend({
  template: ComputeViewVue,

  props: {
    model: {
      required: true,
      type: Object
    }
  },

  data: function() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
  },

  mixins: [GridHolderMixin],

  attached: function() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $mainPanel[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );
  },

  detached: function() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');
  },

  methods: {
    search: function(queryOptions) {
      NavigationActions.openCompute(queryOptions);
    },
    refresh: function() {
      ComputeActions.openCompute(this.model.listView.queryOptions, true);
    },
    loadMore: function() {
      if (this.model.listView.nextPageLink) {
        ComputeActions.openComputeNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    openComputeDetails: function(compute) {
      NavigationActions.openComputeDetails(compute.id);
    }
  }
});

Vue.component('compute-view', ComputeViewVueComponent);

export default ComputeViewVueComponent;
