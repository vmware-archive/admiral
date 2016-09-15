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

var MarkableItemMixin = {
  props: {
    selectModeOn: {
      required: false,
      type: Boolean,
      default: false
    },
    isMarked: {
      required: false,
      type: Boolean,
      default: false
    }
  },
  attached: function() {
    let $el = $(this.$el);

    this.unwatchSelectModeOn = this.$watch('selectModeOn', (selectModeOn) => {
      if (!selectModeOn) { // deselect
        $el.removeClass('marked');
        $el.find('.container-actions').removeClass('hide');
        $el.removeClass('disable-select');
      } else {
        $el.find('.container-actions').addClass('hide');
        $el.addClass('disable-select');
      }
    }, {immediate: true});

    this.unwatchIsMarked = this.$watch('isMarked', (isMarked) => {
      if (isMarked) {
        $el.addClass('marked');
      } else {
        $el.removeClass('marked');
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchSelectModeOn();
    this.unwatchIsMarked();
  }
};


export default MarkableItemMixin;
