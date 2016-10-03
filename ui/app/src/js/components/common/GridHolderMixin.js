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

let shallowCloneFromChildToParent = function($child, $parent, $cloneChild) {
  var $clone = $('<' + $child.prop('tagName') + '>', {
    class: $child.attr('class') + ' notransition'
  });
  $clone.append($cloneChild);

  if ($child[0] === $parent[0]) {
    return $clone;
  } else {
    return shallowCloneFromChildToParent($child.parent(), $parent, $clone);
  }
};

const LIST_MOUSE_OVER_DELAY = 200;

var GridHolderMixin = {
  data: function() {
    return {
      preferredGridWidth: '',
      listMouseover: false
    };
  },
  methods: {
    /**
     * Method that calculates the width of an element without dealing with transition state.
     * Useful when you want to calculate and set the width of an element to be the one that will be
     * after the transition. For example when you animate the width of a container that holds other
     * layout elements that are calculated based on the width of the container, it is helpful and
     * better UI to re-layout the elements prior the transition and re-layout them based on the
     * final width expected width of the container. Remember to call
     * unsetPostTransitionTargetWidth($targetEl) after the transition.
     */
    setPreTransitionGridTargetWidth: function($targetEl) {
      var gridSelector = '.list-view > .grid-container .grid';
      var $grid = $targetEl.find(gridSelector);
      if ($grid.length !== 1) {
        throw new Error('Unexpected number of grids');
      }

      var $partialTargetElClone = shallowCloneFromChildToParent($grid, $targetEl);
      var $partialGridClone = $partialTargetElClone.find(gridSelector);

      $targetEl.parent().append($partialTargetElClone);

      this.preferredGridWidth = $partialGridClone.width();

      $partialTargetElClone.remove();
    },

    unsetPostTransitionGridTargetWidth: function() {
      this.preferredGridWidth = '';
    },

    onListMouseEnter: function() {
      if (this.model && this.model.selectedItem) {
        clearTimeout(this.listMouseoverTimeout);
        this.listMouseoverTimeout = setTimeout(this.onListMouseOverActual, LIST_MOUSE_OVER_DELAY);
      }
    },

    onListMouseOverActual: function() {
      this.listMouseover = true;
      this.repositionListView();
    },

    onListMouseLeave: function() {
      clearTimeout(this.listMouseoverTimeout);
      if (this.listMouseover) {
        this.listMouseover = false;
        this.repositionListView();
      }
    }
  },
  computed: {
    isShowingSearchResults: function() {
      var listView = this.model.listView;
      if (!listView) {
        return false;
      }

      return listView && listView.queryOptions && listView.searchedItems !== false;
    },

    itemsCount: function() {
      var listView = this.model.listView;
      if (!listView) {
        return 0;
      }

      var itemsCount = listView.itemsCount;
      if (itemsCount) {
        return itemsCount;
      }
      var items = listView.items;

      return items ? Object.keys(items).length : 0;
    }
  }
};

export default GridHolderMixin;
