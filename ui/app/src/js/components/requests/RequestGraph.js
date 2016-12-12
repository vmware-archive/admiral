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
import RequestGraphGeneralInfoItemVue from 'components/requests/RequestGraphGeneralInfoItem'; //eslint-disable-line
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

    this.scale = 1;

    window.addEventListener('mousewheel', (e) => {
      if (e.ctrlKey) {
        e.preventDefault();
        e.stopImmediatePropagation();

        var delta = 0;

        if (e.wheelDelta > 0) {
          delta = 0.1;
        } else if (e.wheelDelta < 0) {
          delta = -0.1;
        }

        if (this.scale < 1) {
          delta /= 3;
        }

        this.scale += delta;
        this.scale = Math.max(this.scale, 0.1);
        this.scale = Math.min(this.scale, 10);

        $(this.$el).find('.graph-container').css({
          transform: 'scale(' + this.scale + ')',
          'transform-origin': 'top left'
        });
      }
    }, false);
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
        Container: $(this.$el).find('.graph-container')[0]
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

        // connected states
        var source = null;
        if (stage.transitionSource) {
          source = $(this.$el).find('.task-stage.visited[data-task="' +
            stage.transitionSource.documentSelfLink + '"][data-stage="' +
            stage.transitionSource.subStage + '"][data-updatetime="' +
            stage.transitionSource.documentUpdateTimeMicros + '"]');
          if (source.length === 0) {
            continue;
          } else {
            var offset = source.position();
            currentY = offset.top + source.height() + PADDINGY;
          }
        }

        var taskStage = $(this.$el).find('.task-stage[data-task="' +
            stage.documentSelfLink + '"][data-stage="' +
            stage.taskSubStage + '"][data-updatetime="' +
            stage.documentUpdateTimeMicros + '"]');

        taskStage.css('top', currentY + 'px').addClass('visited');
        taskStage.css('background-color', this.getTaskTypeColor(stage.documentSelfLink));

        taskStage.tooltip({html: true});

        if (source) { // draw connection
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
    },
    showDate: function(timeStamp) {
      var date = new Date(timeStamp / 1000); // timestamp is in micro seconds

      return date.toUTCString();
    },

    displayTasksType: function(link) {
      if (link.indexOf('reservation-tasks') > -1) {
        return 'Reservation';
      } else if (link.indexOf('placement-tasks') > -1) {
        return 'Placement';
      } else if (link.indexOf('allocation-tasks') > -1) {
        return 'Allocation';
      } else if (link.indexOf('requests') > -1) {
        return 'Request';
      } else if (link.indexOf('configure-host') > -1) {
        return 'Configure Host';
      } else if (link.indexOf('composition-tasks') > -1) {
        return 'Composition Tasks';
      } else if (link.indexOf('composition-sub-tasks') > -1) {
        return 'Composition Sub Tasks';
      } else if (link.indexOf('host-removal-operations') > -1) {
        return 'Host Removal';
      } else if (link.indexOf('provision-container-network-tasks') > -1) {
        return 'Provision Network';
      } else if (link.indexOf('composition-removal-tasks') > -1) {
        return 'Remove Composition';
      } else if (link.indexOf('resource-removal-operations') > -1) {
        return 'Resource Removal';
      }

      return link;
    },

    getTaskTypeColor: function(link) {
      let taskType = this.displayTasksType(link);

      if (taskType === 'Reservation') {
        return 'yellow';
      } else if (taskType === 'Placement') {
        return 'pink';
      } else if (taskType === 'Allocation') {
        return 'aliceblue';
      } else if (taskType === 'Request') {
        return 'lightgreen';
      } else if (taskType === 'Configure Host') {
        return '#FF946D';
      } else if (taskType === 'Composition Tasks') {
        return '#E9E3FF';
      } else if (taskType === 'Composition Sub Tasks') {
        return 'lightblue';
      } else if (taskType === 'Host Removal') {
        return '#FF946D';
      }

      return 'lightgrey';
    },

    displayState: function(taskLink, subStage, stage) {
      let taskType = this.displayTasksType(taskLink);

      if (subStage === 'CREATED') {
        if (stage === 'STARTED') {
          return taskType + ' Starting';
        }
        return ' Created';
      } else if (subStage === 'RESERVING') {
        return 'Reserving';
      } else if (subStage === 'PLACEMENT') {
        return 'Placing';
      } else if (subStage === 'HOSTS_SELECTED') {
        if (stage === 'STARTED') {
          return 'Selecting Hosts';
        }
        return 'Hosts selected for placement';
      } else if (subStage === 'RESERVATION_SELECTED') {
         return 'Reservation selected';
      } else if (subStage === 'RESERVED') {
        return 'Reservation completed';
      } else if (subStage === 'ALLOCATING') {
        return 'Allocation Started';
      } else if (subStage === 'ALLOCATED') {
        if (stage === 'STARTED') {
          return 'Allocation completed';
        }
        return 'Allocated';
      } else if (subStage === 'COMPLETED') {
        if (stage === 'STARTED') {
          return taskType + ' Completing';
        }
        if (stage === 'FINISHED') {
          return taskType + ' Finished';
        }

        return 'Completed';
      } else if (subStage === 'SELECTED') {
        return 'Selection Completed';
      } else if (subStage === 'CONTEXT_PREPARED') {
        if (stage === 'STARTED') {
          return 'Preparing Context';
        }

        return 'Context Prepared';
      } else if (subStage === 'RESOURCES_NAMED') {
        return 'Naming Resources';
      } else if (subStage === 'RESOURCES_LINKS_BUILT') {
        return 'Building Resource Links';
      } else if (subStage === 'PLACEMENT_HOST_SELECTED') {
        return 'Selected Host for Placement';
      } else if (subStage === 'START_PROVISIONING') {
        return 'Provisioning Started';
      } else if (subStage === 'PROVISIONING') {
        return 'Provisioning';
      } else if (subStage === 'ERROR') {
        return taskType + ': Error occurred';
      } else if (subStage === 'QUERYING_GLOBAL') {
        if (taskType === 'Reservation') {
          return 'Checking Global Reservations';
        }
      } else if (subStage === 'SELECTED_GLOBAL') {
        if (taskType === 'Reservation') {
          return 'Selected Global Reservation';
        }
      } else if (subStage === 'PLACEMENT_GLOBAL') {
        if (stage === 'STARTED') {
          return 'Started Global Placement';
        }

        return 'Global Placement';
      } else if (subStage === 'COMPONENT_CREATED') {
        return 'Component created';
      } else if (subStage === 'DEPENDENCY_GRAPH') {
        return 'Creating dependency graph';
      } else if (subStage === 'DISTRIBUTING') {
        return taskType + ' Distributing';
      } else if (subStage === 'NOTIFY') {
        return taskType + ' Notifying workers';
      } else if (subStage === 'PREPARE_EXECUTE') {
        return 'Preparing to execute tasks';
      } else if (subStage === 'EXECUTE') {
        return 'Execution started';
      } else if (subStage === 'EXECUTING') {
        return 'Execution in progress';
      } else if (subStage === 'FAILED') {
        return taskType + ' Failed';
      } else if (subStage === 'ERROR_PROVISIONING') {
        return 'Error in Provisioning';
      } else if (subStage === 'INSTANCES_REMOVING') {
        return 'Removing instances';
      } else if (subStage === 'INSTANCES_REMOVED') {
        return 'Instances removal finished';
      } else if (subStage === 'COMPOSITE_REMOVING') {
        return 'Removing Composition';
      } else if (subStage === 'REMOVING_RESOURCE_STATES') {
        return 'Removing resource states';
      }

      return subStage;
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
