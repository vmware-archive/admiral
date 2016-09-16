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

var VueGrid = Vue.extend({
  template: '<div class="grid"><slot></slot></div>',
  props: {
    contextSelected: {},
    count: 0,
    preferredWidth: {},
    sortByDomIndex: {},
    throttle: false
  },
  attached: function() {
    this.unwatchContextSelected = this.$watch('contextSelected', (contextSelected) => {
      this.contextSelected = contextSelected;
      Vue.nextTick(() => {
        this.layout();
      });
    });
    this.unwatchPreferredWidth = this.$watch('preferredWidth', (newValue, oldValue) => {
      if (newValue !== '' && oldValue !== '' && newValue > oldValue) {
        newValue = '';
      }
      $(this.$el).css({
        width: newValue
      });
      this.layout();
    });
    this.unwatchItemCount = this.$watch('count', (count) => {
      this.count = count;
    });
    $(window).on('resize', this.throttleLayout);
  },
  detached: function() {
    this.unwatchContextSelected();
    this.unwatchPreferredWidth();
    this.unwatchItemCount();
    $(window).off('resize', this.throttleLayout);
  },
  methods: {
    throttleLayout: function() {
      if (!this.throttle) {
        this.throttle = true;
        Vue.nextTick(() => {
          this.layout();
          this.throttle = false;
        });
      }
    },
    layout: function() {

      let el = $(this.$el);
      let width = el.width();
      let items = el.children();
      let length = items.size();
      if (length === 0) {
        el.height(0);
        return;
      }

      let height = Math.max.apply(null, items.map((index, element) =>
          $(element).height()));

      let minWidth = parseInt(items.css('minWidth'), 10);
      let maxWidth = parseInt(items.css('maxWidth'), 10);
      let marginHeight =
          parseInt(items.css('marginTop'), 10) + parseInt(items.css('marginBottom'), 10);
      let marginWidth =
          parseInt(items.css('marginLeft'), 10) + parseInt(items.css('marginRight'), 10);
      let columns = Math.floor(width / (minWidth + marginWidth));
      let columnsToUse = Math.max(Math.min(columns, length), 1);
      let rows = Math.floor(length / columnsToUse);
      let itemWidth = Math.min(Math.floor(width / columnsToUse) - marginWidth, maxWidth);
      let itemSpacing = columnsToUse === 1 || columns > length ? marginWidth :
          (width - marginWidth - columnsToUse * itemWidth) / (columnsToUse - 1);
      let visible = !this.count || length === this.count ? length : rows * columnsToUse;

      let count = 0;
      for (let i = 0; i < visible; i++) {
        let item = $(items.get(i));
        item.css({
          left: (i % columnsToUse) * (itemWidth + itemSpacing),
          top: Math.floor(count / columnsToUse) * (height + marginHeight),
          width: itemWidth
        });
        if (!item.hasClass('context-selected')) {
          if (item.is(':hidden') && item.height() !== 0) {
            item.show();
          }
          if (item.height() !== 0) {
            count++;
          }
        }
      }

      for (let i = visible; i < length; i++) {
        $(items.get(i)).hide();
      }

      el.height(Math.ceil(count / columnsToUse) * (height + marginHeight));

      Vue.nextTick(() => {
        this.$dispatch('layout-complete');
      });
    }
  }
});

Vue.component('grid', VueGrid);

var VueGridItem = Vue.extend({
  template: '<div class="grid-item" transition="grid-item-fade"><slot></slot></div>',
  attached: function() {
    this.$parent.throttleLayout();
  },
  detached: function() {
    if (this.$parent) {
      this.$parent.throttleLayout();
    }
  }
});

Vue.component('grid-item', VueGridItem);

Vue.transition('grid-item-fade', {
  stagger: function() {
    return 0;
  },
  enter: function(el, done) {
    done();
  },
  leave: function(el, done) {
    done();
  }
});

export default VueGrid;

