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

import VueTextInput from 'components/common/VueTextInput'; //eslint-disable-line
import SubnetworkEditorVue from 'components/subnetworks/SubnetworkEditorVue.html';
import { SubnetworksActions } from 'actions/Actions';

export default Vue.component('subnetwork-editor', {
  template: SubnetworkEditorVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    validationErrors() {
      return (this.model.validationErrors && this.model.validationErrors._generic);
    }
  },
  data() {
    return {
      name: this.model.item.name,
      cidr: this.model.item.subnetCIDR,
      saveDisabled: !this.model.item.documentSelfLink,
      tags: this.model.item.tags || []
    };
  },
  methods: {
    cancel($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      SubnetworksActions.cancelEditSubnetwork();
    },
    save($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      let toSave = this.getModel();
      if (toSave.documentSelfLink) {
        SubnetworksActions.updateSubnetwork(toSave, this.tags);
      } else {
        SubnetworksActions.createSubnetwork(toSave, this.tags);
      }
    },
    onNameChange(name) {
      this.name = name;
      this.saveDisabled = this.isSaveDisabled();
    },
    onCidrChange(cidr) {
      this.cidr = cidr;
      this.saveDisabled = this.isSaveDisabled();
    },
    onTagsChange(tags) {
      this.tags = tags;
    },
    isSaveDisabled() {
      return !this.name || !this.cidr;
    },
    getModel() {
      return $.extend({}, this.model.item, {
        name: this.name,
        subnetCIDR: this.cidr
      });
    }
  }
});
