/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import VolumeRequestFormVue from 'components/volumes/VolumeRequestFormVue.html';
import VolumeDefinitionForm from 'components/volumes/VolumeDefinitionForm'; // eslint-disable-line
import HostPicker from 'components/networks/HostPicker';
import utils from 'core/utils';
import { VolumeActions } from 'actions/Actions';

var VolumeRequestForm = Vue.extend({
  template: VolumeRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object
    },
    fromResource: {
      type: Boolean
    }
  },

  components: {
    hostPicker: HostPicker
  },

  data: function() {
    return {
      disableCreatingVolumeButton: true
    };
  },

  attached: function() {
    var _this = this;

    $(this.$el).on('change input', function() {
      toggleButtonsState.call(_this);
    });

    this.unwatchModel = this.$watch('model.definitionInstance', () => {
      this.disableCreatingVolumeButton = true;
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchModel();
  },

  methods: {
    createVolume: function() {
      var volumeForm = this.$refs.volumeEditForm;

      var validationErrors = volumeForm.validate();
      if (!validationErrors) {
        var volumeDef = volumeForm.getVolumeDefinition();

        // TODO for now only one host can be selected
        // TODO only hosts supporting the driver should be available for selection
        var hosts = this.$refs.hostPicker.getHosts();
        var hostIds = hosts.map(h => utils.getDocumentId(h.documentSelfLink));

        this.savingVolume = true;

        VolumeActions.createVolume(volumeDef, hostIds);
      }
    }
  },

  events: {
    'change': function() {
      toggleButtonsState.call(this);
    }
  }
});

var toggleButtonsState = function() {
  let volumeName = this.$refs.volumeEditForm.getVolumeDefinition().name;
  let host = this.$refs.hostPicker.getHosts();
  let hostSelected = host && (host.length > 0);

  this.disableCreatingVolumeButton = !volumeName || !hostSelected;
};

Vue.component('volume-request-form', VolumeRequestForm);

export default VolumeRequestForm;
