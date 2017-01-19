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

import { MachineActions, MachinesContextToolbarActions } from 'actions/Actions';
import Tags from 'components/common/Tags';
import MachineEditViewVue from 'components/machines/MachineEditViewVue.html';

export default Vue.component('machine-edit-view', {
  template: MachineEditViewVue,
  props: {
    model: {
      default: () => ({
        contextView: {}
      }),
      required: true,
      type: Object
    }
  },
  data: function() {
    return {
      templateContent: ''
    };
  },
  computed: {
    validationErrors() {
      return this.model.validationErrors || {};
    },
    activeContextItem() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded() {
      return this.model.contextView && this.model.contextView.expanded;
    }
  },
  attached() {
    this.tagsInput = new Tags($(this.$el).find('#tags .tags-input'));
    this.unwatchModel = this.$watch('model', (model, oldModel) => {
        oldModel = oldModel || { item: {} };
        if (model.item.tags !== oldModel.item.tags) {
          this.tagsInput.setValue(model.item.tags);
          this.tags = this.tagsInput.getValue();
        }
    }, {immediate: true});
  },
  detached() {
    this.unwatchModel();
  },
  methods: {
    browseFile: function($event) {
      $event.preventDefault();
      $event.stopImmediatePropagation();

      $(this.$el).find('input.upload').trigger('click');
    },
    onFileChange: function($event) {
      var files = $event.target.files;
      if (!files.length) {
        return;
      }
      this.loadFromFile(files[0]);
    },
    loadFromFile: function(file) {
      var reader = new FileReader();
      reader.onload = (e) => {
        this.templateContent = e.target.result;
      };
      reader.readAsText(file);
    },
    importTemplate: function() {
      if (this.templateContent) {
        MachineActions.createMachine(this.templateContent);
      }
    },
    onPlacementZoneChange(placementZone) {
      this.placementZone = placementZone;
    },
    saveMachine() {
      let model = {
        dto: this.model.item.dto,
        resourcePoolLink: this.placementZone ? this.placementZone.documentSelfLink : null,
        selfLinkId: this.model.item.selfLinkId
      };
      let tags = this.tagsInput.getValue();
      MachineActions.updateMachine(model, tags);
    },
    openToolbarPlacementZones: MachinesContextToolbarActions.openToolbarPlacementZones,
    closeToolbar: MachinesContextToolbarActions.closeToolbar,
    createPlacementZone: MachinesContextToolbarActions.createPlacementZone,
    managePlacementZones: MachinesContextToolbarActions.managePlacementZones

  }
});
