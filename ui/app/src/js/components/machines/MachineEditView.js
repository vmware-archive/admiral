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
import VueTags from 'components/common/VueTags'; //eslint-disable-line
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
      tags: this.model.item.tags || [],
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
    onTagsChange(tags) {
      this.tags = tags;
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
      MachineActions.updateMachine(model, this.tags);
    },
    openToolbarPlacementZones: MachinesContextToolbarActions.openToolbarPlacementZones,
    closeToolbar: MachinesContextToolbarActions.closeToolbar,
    createPlacementZone: MachinesContextToolbarActions.createPlacementZone,
    managePlacementZones: MachinesContextToolbarActions.managePlacementZones

  }
});
