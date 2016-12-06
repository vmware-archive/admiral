import * as actions from 'actions/Actions';
import services from 'core/services';
import constants from 'core/constants';
import PlacementZonesStore from 'stores/PlacementZonesStore';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

const CHECK_INTERVAL_MS = 1000;

let ClosuresStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],

  init: function() {
    PlacementZonesStore.listen((placementZonesData) => {
      this.setInData(['placementZones'], placementZonesData.items);

      this.emitChange();
    });
  },

  listenables: [
    // actions.TemplateActions,
    actions.ClosureActions,
    actions.ClosureContextToolbarActions,
    actions.NavigationActions,
    actions.PlacementZoneActions
  ],

  onOpenClosures: function() {
    this.setInData(['tasks', 'editingItemData'], null);
    this.setInData(['contextView'], {});
    this.setInData(['taskAddView'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['tasks', 'items'], constants.LOADING);
      operation.forPromise(services.loadClosures())
        .then((tasksResult) => {
          if (tasksResult) {
            this.processTasks(tasksResult);
          }
        });

    }

    this.emitChange();

    actions.PlacementZonesActions.retrievePlacementZones();
  },

  processTasks: function(tasksResult) {
    // Transforming from associative array to array
    var tasks = [];
    for (var key in tasksResult) {
      if (tasksResult.hasOwnProperty(key)) {
        var task = tasksResult[key];
        tasks.push(task);
      }
    }

    this.setInData(['tasks', 'items'], tasks);
    this.emitChange();
  },

  onOpenAddClosure: function(closureDescription) {
    var taskAddView = {
      contextView: {}
    };
    if (closureDescription) {
      this.setInData(['tasks', 'editingItemData', 'item'], closureDescription);
      this.setInData(['taskAddView'], taskAddView);
    } else {
      this.setInData(['taskAddView'], taskAddView);
    }
    this.emitChange();

    actions.PlacementZonesActions.retrievePlacementZones();

    if (closureDescription) {
      var _this = this;
      _this.loadTaskData(closureDescription);
    }
  },
  onEditClosure: function(closureDescription) {
    console.log('Executing edit on closure: ' + closureDescription.name);
    services.editClosure(closureDescription).then((request) => {
      console.log('Closure changed successfully!' + request);

      this.setInData(['tasks', 'editingItemData', 'item'], request);
      this.emitChange();
    }).catch(this.onGenericCreateError);
  },

  loadTaskData: function(closureDescription) {
    var _this = this;

    Promise.all([
      services.loadPlacementZone(closureDescription.placementZoneId)
    ])
      .then(function([placementZone]) {
        _this.setInData(['tasks', 'editingItemData', 'placementZone'], placementZone);
        _this.emitChange();
      });
  },

  onCreateClosure: function(templateId, closureDescription) {
    if (!templateId) {
      services.createClosure(closureDescription).then((createdClosure) => {
          console.log('Closure created successfully' + createdClosure);
          this.setInData(['tasks', 'editingItemData', 'item'], createdClosure);
          this.setInData(['creatingResource', 'tasks', 'editingItemData', 'item'], createdClosure);
          this.emitChange();
          actions.TemplateActions.openAddClosure(createdClosure);
      }).catch(this.onGenericCreateError);
    } else {
      services.createClosure(closureDescription).then((createdClosure) => {
          services.loadContainerTemplate(templateId).then((template) => {
          console.log('Updating template ID: ' + templateId + ' with: '
           + createdClosure.documentSelfLink);

          template.descriptionLinks.push(createdClosure.documentSelfLink);

          return services.updateContainerTemplate(template);
        }).then(() => {
          console.log('Closure created successfully' + createdClosure);
          this.setInData(['tasks', 'editingItemData', 'item'], createdClosure);
          this.setInData(['creatingResource', 'tasks', 'editingItemData', 'item'], createdClosure);
          this.emitChange();
          actions.TemplateActions.openAddClosure(createdClosure);
        }).catch(this.onGenericEditError);
      }).catch(this.onGenericCreateError);
    }
  },

  onCreateAndRunClosure: function(templateId, closureDescription, inputs) {
    if (!templateId) {
      services.createClosure(closureDescription).then((createdClosure) => {
          console.log('Closure created successfully' + createdClosure);
          this.setInData(['tasks', 'editingItemData', 'item'], createdClosure);
          this.setInData(['creatingResource', 'tasks', 'editingItemData', 'item'], createdClosure);
          this.emitChange();
          actions.TemplateActions.openAddClosure(createdClosure);

          console.log('Executing closure with description: ' + createdClosure.documentSelfLink);
          actions.TemplateActions.runClosure(createdClosure, inputs);
          actions.TemplatesContextToolbarActions.openToolbarClosureResults();
      }).catch(this.onGenericCreateError);
    } else {
      services.createClosure(closureDescription).then((createdClosure) => {
          services.loadContainerTemplate(templateId).then((template) => {
          console.log('Updating template ID: ' + templateId + ' with: '
           + createdClosure.documentSelfLink);

          template.descriptionLinks.push(createdClosure.documentSelfLink);

          return services.updateContainerTemplate(template);
        }).then(() => {
          console.log('Closure created successfully' + createdClosure);
          this.setInData(['tasks', 'editingItemData', 'item'], createdClosure);
          this.setInData(['creatingResource', 'tasks', 'editingItemData', 'item'], createdClosure);
          this.emitChange();
          actions.TemplateActions.openAddClosure(createdClosure);
        }).catch(this.onGenericEditError);
      }).catch(this.onGenericCreateError);
    }
  },

  onDeleteClosure: function(closureDescription) {
    console.log('Calling delete on: ' + closureDescription);
    services.deleteClosure(closureDescription).then((request) => {
      console.log('Closure deleted successfully! ' + request);
      actions.NavigationActions.openClosures();
    }).catch(this.onGenericCreateError);
  },

  runClosure: function(closureDescription, inputs) {
    this.setInData(['tasks', 'monitoredTask'], null);
    this.emitChange();

    console.log('Calling run on: ' + closureDescription.documentSelfLink);
    services.runClosure(closureDescription, inputs).then((request) => {
      console.log('Closure executed:  ' + JSON.stringify(request));
      this.setInData(['tasks', 'monitoredTask'], request);
      this.emitChange();
      if (!this.requestCheckInterval) {
        this.requestCheckInterval = setInterval(this.refreshMonitoredTask, CHECK_INTERVAL_MS);
      }
    }).catch(this.onGenericCreateError);
  },
  refreshMonitoredTask: function() {
    var task = this.data.tasks.monitoredTask;
    if (task) {
      if (task.state === 'FINISHED' || task.state === 'FAILED' || task.state === 'CANCELLED') {
        this.stopTaskRefresh(this.requestCheckInterval);
        this.fetchLogs();
        return;
      } else {
        console.log('Monitoring closure: ' + task.documentSelfLink);
      }
      services.getClosure(task.documentSelfLink).then((fetchedTask) => {
        this.setInData(['tasks', 'monitoredTask'], fetchedTask);
        this.setInData(['tasks', 'monitoredTask', 'taskId'],
          fetchedTask.documentSelfLink.split('/').pop());
        this.emitChange();
        if (fetchedTask.resourceLinks && fetchedTask.resourceLinks.length > 0) {
          this.fetchLogs();
        }
      });
    } else {
      console.warn('No available closure to monitor!');
    }
  },
  resetMonitoredClosure: function() {
    console.log('Resetting monitored closure...');
    this.data.tasks.monitoredTask = null;
    this.emitChange();
  },
  stopTaskRefresh: function(refreshCheckInterval) {
    if (refreshCheckInterval) {
      clearInterval(refreshCheckInterval);
      this.requestCheckInterval = null;
    }
  },
  fetchLogs: function() {
    var task = this.data.tasks.monitoredTask;
    if (typeof task.resourceLinks === 'undefined' || task.resourceLinks.length <= 0) {
      console.log('No resources to fetch logs...');
      this.setInData(['tasks', 'monitoredTask', 'taskLogs'], task.errorMsg);
      this.emitChange();
      return;
    }

    var taskLogResource = task.resourceLinks[0].split('/').pop();
    console.log('Requesting logs from: ' + taskLogResource);
    services.getClosureLogs(taskLogResource).then((fetchedLogs) => {
      this.setInData(['tasks', 'monitoredTask', 'taskLogs'], atob(fetchedLogs.logs));
      this.emitChange();
    });
  },
  onOpenToolbarPlacementZones: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      false);
  },
  onCloseToolbar: function() {
    this.closeToolbar();
  },
  onCreatePlacementZone: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      true);
    actions.PlacementZonesActions.editPlacementZone();
  },
  onManagePlacementZones: function() {
    this.openToolbarItem(constants.CONTEXT_PANEL.RESOURCE_POOLS, PlacementZonesStore.getData(),
      true);
  }


});

export
default ClosuresStore;
