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

import MachinesViewVue from 'MachinesViewVue';
import MachineItem from 'components/machines/MachineItem'; //eslint-disable-line
import MachineDetails from 'components/machines/MachineDetails'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { MachineActions, NavigationActions } from 'actions/Actions';

var MachinesViewVueComponent = Vue.extend({
  template: MachinesViewVue,

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
    var $content = $mainPanel.find('.content');

    $(window).resize(() => {
        $content.scrollTop(0);
        MachineActions.openMachines(this.model.listView.queryOptions);
    });

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
      NavigationActions.openMachines(queryOptions);
    },
    refresh: function() {
      MachineActions.openMachines(this.model.listView.queryOptions);
    },
    loadMore: function() {
      if (this.model.listView.nextPageLink) {
        MachineActions.openMachinesNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    openMachineDetails: function(machine) {
      NavigationActions.openMachineDetails(machine.id);
    }
  }
});

Vue.component('machines-view', MachinesViewVueComponent);

export default MachinesViewVueComponent;
