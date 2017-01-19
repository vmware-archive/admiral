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

import MachinesViewVue from 'components/machines/MachinesViewVue.html';
import MachineItem from 'components/machines/MachineItem'; //eslint-disable-line
import MachineEditView from 'components/machines/MachineEditView'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { MachineActions, NavigationActions } from 'actions/Actions';

export default Vue.component('machines-view', {
  template: MachinesViewVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
  },
  mixins: [GridHolderMixin],
  attached() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $mainPanel[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );
  },
  detached() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');
  },
  computed: {
    searchSuggestions() {
      return constants.MACHINES.SEARCH_SUGGESTIONS;
    }
  },
  methods: {
    goBack() {
      NavigationActions.openMachines(this.model.listView && this.model.listView.queryOptions);
    },
    search(queryOptions) {
      NavigationActions.openMachines(queryOptions);
    },
    refresh() {
      MachineActions.openMachines(this.model.listView.queryOptions, true);
    },
    loadMore() {
      if (this.model.listView.nextPageLink) {
        MachineActions.openMachinesNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    addMachine() {
      NavigationActions.openAddMachine();
    }
  }
});
