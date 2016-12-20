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

import CompositeClosuresDetailsVue from
  'components/containers/closure/CompositeClosuresDetailsVue.html';
import ClosureListItem from 'components/containers/ClosureListItem'; //eslint-disable-line
import ClosureDetails from 'components/containers/ClosureDetails'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import VueToolbarActionButton from 'components/common/VueToolbarActionButton'; //eslint-disable-line
import constants from 'core/constants'; //eslint-disable-line
import utils from 'core/utils';
import { ContainerActions, NavigationActions } from 'actions/Actions';

var CompositeClosuresDetails = Vue.extend({
  template: CompositeClosuresDetailsVue,
  mixins: [GridHolderMixin],
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          listView: {},
          contextView: {}
        };
      }
    }
  },
  data: function() {
    return {
      constants: constants,
      selectionMode: false,
      selectedItems: [],
      lastSelectedItemId: null
    };
  },
  computed: {
    contextExpanded: function() {
      return this.$parent.model.contextView && this.$parent.model.contextView.expanded;
    },
    selectedItemDocumentId: function() {
      return this.model.selectedItem && this.model.selectedItem.documentId;
    },
    hasItems: function() {
      return this.model.listView.items && this.model.listView.items.length > 0;
    }
  },
  attached: function() {
    var $detailsContent = $(this.$el);
    $detailsContent.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $detailsContent[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );

    this.unwatchSelectedItem = this.$watch('model.selectedItem', () => {
      this.repositionListView();
    });
    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($detailsContent);
      });
    });
  },
  detached: function() {
    this.unwatchSelectedItem();
    this.unwatchExpanded();
    var $detailsContent = $(this.$el);
    $detailsContent.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');
  },
  methods: {
    openClosureChildDetails: function(childId) {
      NavigationActions.openClosureDetails(childId, this.model.documentId);
    },

    repositionListView: function() {
      Vue.nextTick(() => {
        var $el = $(this.$el);
        var $smallContextHolder = $el
          .children('.list-view').children('.selected-context-small-holder');
        var top = 0;
        if ($smallContextHolder.length === 1) {
          top = $smallContextHolder.position().top + $smallContextHolder.height();
        }
        $(this.$el)
          .children('.list-view').children('.grid-container')
          .css({transform: 'translate(0px,' + top + 'px)'});
      });
    },

    refresh: function() {
      ContainerActions.openCompositeClosureDetails(this.model.documentId);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    },

    multiSelectionSupported: function() {
      return this.hasItems;
    },

    multiSelectionOperationSupported: function(operation) {
        return operation === constants.RESOURCES.NETWORKS.OPERATION.REMOVE;
    },
    performDeleteBatchOperation: function() {
        this.performBatchOperation('Closure.Delete');
    },
    performBatchOperation: function(operation) {
      let selectedItemIds = this.selectedItems;
      this.clearSelections();

      if (selectedItemIds && selectedItemIds.length > 0) {
          ContainerActions.batchOpClosures(selectedItemIds, operation);
      }
    },
    isMarked: function(item) {
      return !item.system && this.selectedItems.indexOf(item.documentId) > -1;
    },
    clearSelections: function() {
      // hide day2 ops bar
      $(this.$el).find('.title-second-day-operations').addClass('hide');
      // clear data
      this.selectionMode = false;
      this.selectedItems = [];
      this.lastSelectedItemId = null;
      // un-mark items
      $(this.$el).find('.grid-item').removeClass('marked');

        // clear the warnings list. This needs to be done on the 'next tick', otherwise
        // watchers will not see the change.
        let _this = this;
        this.$nextTick(() => {
          _this.containerConnectedAlerts = [];
        });

    },

    toggleSelectionMode: function() {
      this.selectionMode = !this.selectionMode;
      if (this.selectionMode) {
        $(this.$el).find('.title-second-day-operations').removeClass('hide');
      } else {
        this.clearSelections();
      }
    },
    handleItemClick: function($event, item, defaultFn) {
      let itemId = item.documentId;

      if (!this.selectionMode) {
        // standard flow
        if (defaultFn) {
          defaultFn.call(this, itemId);
        }

      } else {
        // selection of items
        $event.stopPropagation();

        if (item.system) {
          // no day 2 ops on system containers
          return;
        }

        let $gridItemEl = $($event.target).closest('.grid-item');
        let wasSelected = $gridItemEl.hasClass('marked');

        if (!wasSelected) {
          $gridItemEl.addClass('marked');
        } else {
          $gridItemEl.removeClass('marked');
        }

        let isSelected = !wasSelected;
        if (isSelected) {
          // add to selected items
          utils.pushNoDuplicates(this.selectedItems, itemId);

          if ($event.shiftKey && this.lastSelectedItemId) {

            let startIndex = this.model.listView.items.findIndex((item) => {
              return item.documentId === this.lastSelectedItemId;
            });
            let lastIndex = this.model.listView.items.findIndex((item) => {
              return item.documentId === itemId;
            });

            if (startIndex > lastIndex) {
              // backwards selection
              let tmp = startIndex;
              startIndex = lastIndex;
              lastIndex = tmp;
            }

            // add the items between the indices
            this.model.listView.items.forEach((item, index) => {
              if (index >= startIndex && index <= lastIndex) {
                utils.pushNoDuplicates(this.selectedItems, item.documentId);
              }
            });
          }

          this.lastSelectedItemId = itemId;
        } else {
          // remove from selected items
          let idxSelectedItem = this.selectedItems.indexOf(itemId);
          if (idxSelectedItem > -1) {
            this.selectedItems.splice(idxSelectedItem, 1);
          }
          this.lastSelectedItemId = null;
        }
      }
    }
  },
  events: {
    'do-action': function(actionName) {
      if (actionName === 'multiRemove') {
        this.performDeleteBatchOperation();
      } else if (actionName === 'multiSelect') {
          // Multi-selection mode
        this.toggleSelectionMode();
      }
    }
  }

});

Vue.component('composite-closure-details', CompositeClosuresDetails);

export default CompositeClosuresDetails;

