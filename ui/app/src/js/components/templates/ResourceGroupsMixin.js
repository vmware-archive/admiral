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

import utils from 'core/utils';

var ResourceGroupsMixin = {
  computed: {
    hasGroups: function() {
      return this.groups && (this.groups.length > 0)
        || this.model.groups && (this.model.groups.length > 0);
    },
    groupOptions: function() {
      if (!this.hasGroups) {
        return null;
      }

      let groups = this.groups || this.model.groups;
      return groups.map((group) => {
        return {
          id: group.id ? group.id : group.documentSelfLink,
          name: group.label ? group.label : group.name
        };
      });
    }
  },
  data: function() {
    return {
      showGroupForProvisioning: false,
      preferredGroupId: null,
      selectedGroup: null
    };
  },
  attached: function() {

    this.unwatchShowGroup = this.$watch('showGroupForProvisioning', () => {
      if (this.showGroupForProvisioning) {
        let _this = this;
        $(this.$el).find('.provisionGroup select').change(function() {
          _this.showSelectionTooltip();
        });

        this.showSelectionTooltip();
      }
    });
  },
  methods: {
    clearSelectionSupported: function() {
      // available only in standalone mode
      return !utils.isApplicationEmbedded();
    },
    showGroups: function() {
      this.showGroupForProvisioning = true;
      this.preferredGroupId = localStorage.getItem('preferredGroupId');

      if (!this.preferredGroupId) {
        if (!this.clearSelectionSupported() && !this.preferredGroupId) {
          let groups = this.groups || this.model.groups;
          if (groups && groups.length > 0) {
            this.preferredGroupId = groups[0].id;
          }
        } else {
          this.preferredGroupId = '';
        }
        localStorage.setItem('preferredGroupId', this.preferredGroupId);
      }
    },
    handleGroup: function(fnToCall, params) {

      if (this.hasGroups) {
        if (!this.showGroupForProvisioning) {

          this.showGroups();
        } else {

          localStorage.setItem('preferredGroupId', this.preferredGroupId);
          this.showGroupForProvisioning = false;

          let groups = this.groups || this.model.groups;
          this.selectedGroup = groups.find((group) => {
            return group.id === this.preferredGroupId;
          });

          if (this.selectedGroup) {
            let group = this.selectedGroup.documentSelfLink
                              ? this.selectedGroup.documentSelfLink : this.selectedGroup.id;
            params.push(group);
          }

          fnToCall.apply(this, params);
        }
      } else {
        fnToCall.apply(this, params);
      }
    },
    toggleGroupsDisplay: function() {
      if (this.hasGroups) {
        this.showGroups();
      } else {
        this.showGroupForProvisioning = false;
      }
    },
    hideGroups: function($event) {
      if ($event.target.tagName === 'SELECT' || $event.target.tagName === 'OPTION') {
        return;
      }

      if (this.showGroupForProvisioning) {
        this.showGroupForProvisioning = false;
      }
    },
    showSelectionTooltip: function() {
      let elProvisionGroupSelect = $(this.$el).find('.provisionGroup select');
      let elSelectedOption = $(elProvisionGroupSelect).find('option:selected');

      $(elProvisionGroupSelect).prop('title', $(elSelectedOption).text().trim());
    }
  }
};

export default ResourceGroupsMixin;
