/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import MachineStatsVue from 'components/machines/MachineStatsVue.html';
import { RadialProgress } from 'admiral-ui-common';
import { formatUtils } from 'admiral-ui-common';

const NA = i18n.t('unavailable');

var MachineStats = Vue.extend({
  template: MachineStatsVue,
  props: {
    model: { required: true }
  },

  ready: function() {
    this.cpuStats = new RadialProgress($(this.$el).find('.cpu-stats')[0]).diameter(150).value(0)
      .majorTitle(NA).label(i18n.t('app.container.details.cpu')).render();
    this.memoryStats = new RadialProgress($(this.$el).find('.memory-stats')[0]).diameter(150)
      .value(0).majorTitle(NA).label(i18n.t('app.container.details.memory')).render();

    this.onDataUpdate(this.model.instance.stats);
  },

  attached: function() {
    this.modelUnwatch = this.$watch('model.instance.stats', this.onDataUpdate);
  },

  detached: function() {
    this.modelUnwatch();
  },

  filters: {
    calculateStatsClass: function(percentage) {
      if (!percentage) {
        return '';
      }

      if (percentage < 50) {
        return 'info';
      }

      if (percentage < 80) {
        return 'warning';
      }

      return 'danger';
    }
  },
  methods: {
    onDataUpdate: function(data) {
      if (data) {
        var cpuPercentage = data.cpuUsage;
        if (typeof cpuPercentage !== 'undefined') {
          this.cpuStats.value(cpuPercentage).majorTitle(null).render();
        } else {
          this.cpuStats.value(0).majorTitle(NA).render();
        }

        var memoryPercentage;
        if (!data.memory || !data.memoryUsage) {
          memoryPercentage = 0;
        } else {
          memoryPercentage = (data.memoryUsage / data.memory) * 100;
        }

        this.cpuPercentage = cpuPercentage;
        this.memoryPercentage = memoryPercentage;

        var memoryUsage = formatUtils.formatBytes(data.memoryUsage);
        var memoryLimit = formatUtils.formatBytes(data.memory);
        this.memoryStats.majorTitle(memoryUsage).minorTitle(memoryLimit).value(memoryPercentage)
          .render();

        if (this.networkStats) {
          // TODO
        }
      } else {
        resetStats.call(this);
      }
    }
  }
});

function resetStats() {
  this.cpuStats.value(0).majorTitle(NA).render();
  this.memoryStats.majorTitle(NA).minorTitle(NA).value(0).render();
  if (this.networkStats) {
    this.networkStats.reset(NA);
  }
}

Vue.component('machine-stats', MachineStats);

export default MachineStats;
