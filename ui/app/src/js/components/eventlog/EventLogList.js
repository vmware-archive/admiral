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

import EventLogListVue from 'components/eventlog/EventLogListVue.html';
import EventLogListItemVue from 'components/eventlog/EventLogListItemVue.html';
import Component from 'components/common/Component';
import InlineDeleteConfirmationTemplate from
  'components/common/InlineDeleteConfirmationTemplate.html';
import utils from 'core/utils';
import * as actions from 'actions/Actions';

var EventLogListVueComponent = Vue.extend({
  template: EventLogListVue,
  props: {
    model: {required: true}
  },
  components: {
    'eventlog-item': {
      template: EventLogListItemVue,

      data: function() {
        return {
          expanded: false
        };
      },

      props: {
        model: {
          required: true
        }
      },

      attached: function() {
        this.unwatchIsHighlighted = this.$watch('model.isHighlighted', (isHighlighted) => {
          if (isHighlighted) {
            this.expanded = true;

            setTimeout(() => {
              this.model.isHighlighted = false;
            }, 3000);
          }
        }, {immediate: true});

        // Handle Confirm Event Logs Item Delete
        $(this.$el).on('click', '.delete-inline-item-confirmation-confirm', (e) => {
          e.preventDefault();

          // Hide cancel button and show loading indication
          var $deleteButton = $(e.currentTarget);
          $deleteButton.prev('.delete-inline-item-confirmation-cancel').addClass('hide');
          $deleteButton.css('float', 'right');
          $deleteButton.find('.fa').removeClass('hide');

          actions.EventLogActions.removeEventLog(this.model.documentSelfLink);
        });

        // Handle Cancel Event Logs Item Delete
        $(this.$el).on('click', '.delete-inline-item-confirmation-cancel', (e) => {
          e.preventDefault();
          this.hideDeleteConfirmationButtons(e);
        });
      },

      detached: function() {
        this.unwatchIsHighlighted();
      },

      methods: {
        getEventLogTypeClass(event) {
          if (utils.isEventLogTypeWarning(event)) {
            return 'event-type-warning';
          }
          if (utils.isEventLogTypeError(event)) {
            return 'event-type-error';
          }
          return 'event-type-info';
        },

        getTimeFromNow(event) {
          return utils.humanizeTimeFromNow(event.documentUpdateTimeMicros);
        },

        toggleExpandDescription($e) {
          $e.preventDefault();
          this.expanded = !this.expanded;
        },

        deleteEventLog: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          let $eventLogItem = $(this.$el);

          let $deleteConfirmationHolder = $(InlineDeleteConfirmationTemplate());
          $deleteConfirmationHolder.height($eventLogItem.outerHeight(false));

          let $actions = $eventLogItem.children('.eventlog-actions');
          $actions.addClass('hide');
          $deleteConfirmationHolder.insertAfter($actions);

          let $deleteConfirmation = $deleteConfirmationHolder
            .find('.delete-inline-item-confirmation');
          utils.slideToLeft($deleteConfirmation);
        },

        hideDeleteConfirmationButtons: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          let $deleteConfirmationHolder = $($event.currentTarget)
            .closest('.delete-inline-item-confirmation-holder');

          utils.fadeOut($deleteConfirmationHolder, function() {
            $deleteConfirmationHolder.remove();
          });

          $(this.$el).children('.eventlog-actions').removeClass('hide');
        }
      }
    }
  },
  attached: function() {
    $(this.$el).find('.nav-item a[href="#all"]').tab('show');
  },
  methods: {
    detached: function() {
      actions.EventLogActions.closeEventLog();
    },

    warningEventLogTypeFilter: function(item) {
      return utils.isEventLogTypeWarning(item);
    },

    errorEventLogTypeFilter: function(item) {
      return utils.isEventLogTypeError(item);
    },

    refresh: function() {
      actions.EventLogActions.openEventLog();
    },

    selectTab($event, itemsType) {
      $event.stopPropagation();
      $event.preventDefault();
      $($event.target).tab('show');
      actions.EventLogActions.selectEventLog(itemsType);
    },

    loadMore: function(itemsType) {
      if (this.model.nextPageLink && itemsType === this.model.itemsType) {
        actions.EventLogActions.openEventLogNext(this.model.nextPageLink);
      }
    },
    close: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.$dispatch('close');
    },
    actionAllowed: function() {
      return utils.actionAllowed();
    }
  },
  events: {
    'do-action': function(actionName) {

      if (actionName === 'deleteAll') {
        // Clear all events
        actions.EventLogActions.clearEventLog();
      }
    }
  }
});

Vue.component('eventlog-list', EventLogListVueComponent);

class EventLogList extends Component {
  constructor() {
    super();

    this.$el = $('<div><eventlog-list v-bind:model="model"></eventlog-list></div>');

    this.vue = new Vue({
      el: this.$el[0],
      data: {
        model: {}
      }
    });
  }

  setData(data) {
    Vue.set(this.vue, 'model', data);
  }

  getEl() {
    return this.$el;
  }
}

export default EventLogList;
