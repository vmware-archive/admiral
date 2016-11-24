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

var VueTitleActionButton = Vue.extend({
  template: `<div class="title-action-button" v-show="show"><a class="btn btn-circle-outline"
              title="{{tooltip}}" v-on:mousedown="pressed()"
                ><i class="fa fa-{{iconName}}"></i></a></div>`,
  props: {
    name: {
      required: true,
      type: String
    },
    iconName: {
      required: false,
      type: String,
      default: 'ship'
    },
    show: {
      required: false,
      type: Boolean,
      default: true
    },
    tooltip: {
      required: false,
      type: String
    },
    confirmable: {
      required: false,
      type: Boolean,
      default: true
    },
    // NORMAL, SPIN, SPIN_TIMEOUT, TOGGLE
    buttonType: {
      required: false,
      type: String,
      default: 'SPIN'
    },
    time: {
      required: false,
      type: Number,
      default: 3000
    },
    stopSpin: {
      required: false,
      type: Boolean,
      default: false
    },
    toggleOff: {
      required: false,
      type: Boolean,
      default: true
    }
  },
  data: function() {
    return {
      toggleOn: false
    };
  },
  attached: function() {
    this.unwatchStopSpin = this.$watch('stopSpin', (stopSpin) => {
      if (stopSpin) {
        this.spinStop();
      }
    });

    this.unwatchToggleOff = this.$watch('toggleOff', (toggleOff) => {
      if (toggleOff) {
        this.toggleOn = false;
        $(this.$el).find('i').removeClass('selected');
        $(this.$el).find('a').removeClass('selected');
      }
    });
  },
  detached: function() {
    this.unwatchStopSpin();
    this.unwatchToggleOff();
  },
  methods: {
    notifyAction: function() {
      if (this.confirmable) {
        this.$dispatch('title-action-confirm', {
          name: this.name,
          title: this.tooltip
        });
      } else {
        this.$dispatch('title-action', this.name);
      }
    },
    pressed: function() {
      // button type is toggable
      if (this.buttonType === 'TOGGLE') {
        this.toggleOn = !this.toggleOn;

        if (this.toggleOn) {
          $(this.$el).find('i').addClass('selected');
          $(this.$el).find('a').addClass('selected');

        } else {
          $(this.$el).find('i').removeClass('selected');
          $(this.$el).find('a').removeClass('selected');
        }
      } else if (this.buttonType === 'SPIN') {
        $(this.$el).find('i').removeClass('fa-' + this.iconName);
        $(this.$el).find('i').addClass('fa-spinner');
        $(this.$el).find('i').addClass('fa-spin');

      } else if (this.buttonType === 'SPIN_TIMEOUT') {
        // self-spin: $(this.$el).find('i').addClass('fa-spin');
        $(this.$el).find('i').removeClass('fa-' + this.iconName);
        $(this.$el).find('i').addClass('fa-spinner');
        $(this.$el).find('i').addClass('fa-spin');

        clearTimeout(this.timeoutId);

        this.timeoutId = setTimeout(() => {
          this.spinStop();
        }, this.time);
      }

      return this.notifyAction();
    },

    spinStop: function() {
      // self-spin: $(this.$el).find('i').removeClass('fa-spin');
      $(this.$el).find('i').removeClass('fa-spinner');
      $(this.$el).find('i').removeClass('fa-spin');
      $(this.$el).find('i').addClass('fa-' + this.iconName);
    }
  }
});

Vue.component('title-action-button', VueTitleActionButton);

export default VueTitleActionButton;
