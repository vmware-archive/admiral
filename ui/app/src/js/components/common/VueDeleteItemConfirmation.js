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

import utils from 'core/utils';

var VueDeleteItemConfirmation = Vue.extend({
  template: `<div class="delete-inline-item-confirmation-holder">
              <div class="delete-inline-item-confirmation">
                <a href="#" class="delete-inline-item-confirmation-cancel"
                            v-on:click="cancelDelete($event)"><span>{{i18n('cancel')}}</span></a>
                <a href="#" class="delete-inline-item-confirmation-confirm"
                            v-on:click="confirmDelete($event)">
                  <span>{{i18n('delete')}}
                  <i class="fa fa-spinner fa-spin loader-inline not-underlined hide">
                  </i></span></a>
              </div>
            </div>`,

  props: {
    show: {
      required: true,
      type: Boolean,
      default: false
    }
  },
  data: function() {
    return {
      loading: false
    };
  },

  attached: function() {
    this.unwatchShow = this.$watch('show', (newValue, oldValue) => {
        if (newValue !== oldValue) {
          if (newValue) {
            this.showConfirmation();
          } else {
            this.hideConfirmation();
          }
        }
      });
  },

  detached: function() {
    this.unwatchShow();
  },

  methods: {
    showConfirmation: function() {
      $(this.$el).removeClass('hide');
      $(this.$el).css({
        opacity: 1
      });

      let $deleteConfirmation = $(this.$el).find('.delete-inline-item-confirmation');
      utils.diagonalEmerge($deleteConfirmation);
    },

    confirmDelete: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      // Hide cancel button and show loading indication
      var $deleteButton = $(this.$el).find('.delete-inline-item-confirmation-confirm');
      $deleteButton.prev('.delete-inline-item-confirmation-cancel').addClass('hide');
      $deleteButton.css('float', 'right');
      $deleteButton.find('span').addClass('not-underlined');
      $deleteButton.find('.fa').removeClass('hide');

      if (!this.loading) {
        this.loading = true;
        this.$dispatch('confirm-delete');
      }
    },

    cancelDelete: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.hideConfirmation('cancel-delete');
    },

    hideConfirmation: function(eventToDispatch) {
      var $deleteConfirmationHolder = $(this.$el);
      var $deleteButton = $(this.$el).find('.delete-inline-item-confirmation-confirm');

      var _this = this;
      utils.fadeOut($deleteConfirmationHolder, function() {
        $deleteConfirmationHolder.addClass('hide');
        $deleteButton.prev('.delete-inline-item-confirmation-cancel').removeClass('hide');
        $deleteButton.find('.fa').addClass('hide');
        $deleteButton.find('span').removeClass('not-underlined');

        _this.loading = false;
        if (eventToDispatch) {
          _this.$dispatch(eventToDispatch);
        }
      });
    }
  }
});

Vue.component('delete-confirmation', VueDeleteItemConfirmation);

export default VueDeleteItemConfirmation;
