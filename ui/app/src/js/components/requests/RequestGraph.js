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

import RequestGraphVue from 'components/requests/RequestGraphVue.html';
import Component from 'components/common/Component';

var RequestGraphVueComponent = Vue.extend({
  template: RequestGraphVue,

  props: {
    model: { required: true }
  },

  computed: {
    tasks: function() {
      return this.model && this.model.graph && this.model.graph.tasks;
    }
  },

  attached: function() {
    this.modelUnwatch = this.$watch('tasks', this.updateTasks, {immediate: true});
  },

  detached: function() {
    this.modelUnwatch();
  },

  methods: {
    updateTasks: function(tasks) {
      if (!tasks) {
        return;
      }

      this.jsplumbInstance = jsPlumb.getInstance({
        Connector: ['StateMachine'],
        PaintStyle: {
          lineWidth: 1,
          strokeStyle: '#333333',
          outlineColor: 'transparent',
          outlineWidth: 10
        },
        Endpoint: ['Dot', { radius: 1 }],
        EndpointStyle: { fillStyle: '#ffa500' },
        ConnectorPaintStyle: {
          lineWidth: 1
        },
        ConnectorHoverPaintStyle: {
          lineWidth: 3
        },
        HoverPaintStyle: {
          lineWidth: 3,
          outlineColor: 'transparent',
          outlineWidth: 10
        },
        ConnectionOverlays: [['Arrow', {
          location: 1
        }]],
        Container: $(this.$el).find('.request-graph')[0]
      });

      this.jsplumbInstance['pointer-events'] = 'all';

      Vue.nextTick(() => {
        this.visitTasks(tasks.asMutable({deep: true}));
      });
    },

    visitTasks: function(tasks) {
      if (!tasks || !tasks.length) {
        return;
      }

      var stages = [];

      for (var i = 0; i < tasks.length; i++) {
        var task = tasks[i];
        stages = stages.concat(task.stages);
      }

      stages.sort((a, b) => {
        return a.documentUpdateTimeMicros - b.documentUpdateTimeMicros;
      });

      this.visitStages(stages);
    },

    visitStages: function(stages) {
      if (!stages || !stages.length) {
        return;
      }

      const PADDINGY = 50;
      var currentY = PADDINGY + 100;
      for (var i = 0; i < stages.length; i++) {
        var stage = stages[i];
        var source = null;
        if (stage.transitionSource) {
          source = $(this.$el).find('.task-stage.visited[data-task="' +
            stage.transitionSource.documentSelfLink + '"][data-stage="' +
            stage.transitionSource.subStage + '"][data-updatetime="' +
            stage.transitionSource.documentUpdateTimeMicros + '"]');
          if (source.length === 0) {
            return;
          } else {
            var offset = source.offset();
            currentY = offset.top + source.height() + PADDINGY;
          }
        }

        var taskStage = $(this.$el).find('.task-stage[data-task="' +
            stage.documentSelfLink + '"][data-stage="' +
            stage.taskSubStage + '"][data-updatetime="' +
            stage.documentUpdateTimeMicros + '"]');

        taskStage.css('top', currentY + 'px').addClass('visited');

        if (source) {
          this.jsplumbInstance.connect({
            source: source[0],
            target: taskStage[0],
            anchors: [
              ['Perimeter', { shape: 'Rectangle' }],
              ['Perimeter', { shape: 'Rectangle' }]
            ]
          });
        }
      }
    }
  }
});

Vue.component('request-graph', RequestGraphVueComponent);

class RequestGraph extends Component {
  constructor() {
    super();
    this.$el = $('<div>').append('<request-graph v-bind:model="currentModel">');
  }

  getEl() {
    return this.$el;
  }

  attached() {
    this.vue = new Vue({
      el: this.$el[0],
      data: {
        currentModel: {}
      }
    });
  }

  detached() {
    if (this.vue) {
      this.vue.$destroy();
      this.vue = null;
    }
  }

  setData(data) {
    Vue.set(this.vue, 'currentModel', data);
  }
}

export default RequestGraph;
