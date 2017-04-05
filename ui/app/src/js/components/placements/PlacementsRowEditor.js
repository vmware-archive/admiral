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

import PlacementsRowEditTemplate from 'components/placements/PlacementsRowEditTemplate.html';
import {
  PlacementContextToolbarActions, PlacementActions
}
from 'actions/Actions';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Alert from 'components/common/Alert';
import constants from 'core/constants';
import utils from 'core/utils';
import { formatUtils } from 'admiral-ui-common';
import ft from 'core/ft';

const placementZoneManageOptions = [{
  id: 'pz-create',
  name: i18n.t('app.placementZone.createNew'),
  icon: 'plus'
}, {
  id: 'pz-manage',
  name: i18n.t('app.placementZone.manage'),
  icon: 'pencil'
}];

const GROUPS_MANAGE_OPTIONS = [{
  id: 'group-create',
  name: i18n.t('app.group.createNew'),
  icon: 'plus'
}, {
  id: 'group-manage',
  name: i18n.t('app.group.manage'),
  icon: 'pencil'
}];

const deploymentPolicyManageOptions = [{
  id: 'policy-create',
  name: i18n.t('app.deploymentPolicy.createNew'),
  icon: 'plus'
}, {
  id: 'policy-manage',
  name: i18n.t('app.deploymentPolicy.manage'),
  icon: 'pencil'
}];

function PlacementsRowEditor() {
  let model = {
    isEmbeded: utils.isApplicationEmbedded(),
    isDeploymentPoliciesAvailable: ft.isDeploymentPoliciesEnabled()
  };
  this.$el = $(PlacementsRowEditTemplate(model));

  if (utils.isApplicationEmbedded()) {
    let groupFromEl = this.$el.find('.group');
    let groupFormLabelEl = groupFromEl.find('.control-label');
    groupFormLabelEl.append('<span class="requiredFieldMark">*</span>');
  }

  this.$el.find('.fa-question-circle').tooltip();

  this.alert = new Alert(this.$el, this.$el.find('.inline-edit'), false);

  this.placementGroupInput = new GroupInput(this.$el.find('.placementGroupInput'), () =>
    toggleButtonsState.call(this));

  let placementZoneInputEl = this.$el.find('.placementZone .form-control');
  this.placementZoneInput = new DropdownSearchMenu(placementZoneInputEl, {
    title: i18n.t('app.placement.edit.selectPlacementZone'),
    searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
      entity: i18n.t('app.placementZone.entity')
    })
  });

  this.placementZoneInput.setManageOptions(placementZoneManageOptions);
  this.placementZoneInput.setManageOptionSelectCallback(function(option) {
    if (option.id === 'pz-create') {
      PlacementContextToolbarActions.createPlacementZone();
    } else {
      PlacementContextToolbarActions.managePlacementZones();
    }
  });
  this.placementZoneInput.setClearOptionSelectCallback(() => toggleButtonsState.call(this));

  this.placementZoneInput.setOptionSelectCallback(() => toggleButtonsState.call(this));

  var deploymentPolicyEl = this.$el.find('.deploymentPolicy .form-control');
  this.deploymentPolicyInput = new DropdownSearchMenu(deploymentPolicyEl, {
    title: i18n.t('app.placement.edit.selectDeploymentPolicy'),
    searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
      entity: i18n.t('app.deploymentPolicy.entity')
    })
  });

  this.deploymentPolicyInput.setManageOptions(deploymentPolicyManageOptions);
  this.deploymentPolicyInput.setManageOptionSelectCallback(function(option) {
    if (option.id === 'policy-create') {
      PlacementContextToolbarActions.createDeploymentPolicy();
    } else {
      PlacementContextToolbarActions.manageDeploymentPolicies();
    }
  });

  this.deploymentPolicyInput.setOptionSelectCallback(() => toggleButtonsState.call(this));

  if (!ft.isDeploymentPoliciesEnabled()) {
    this.$el.find('.inline-edit-holder').prop('colspan', 7);
  }
  addEventListeners.call(this);
}

PlacementsRowEditor.prototype.getEl = function() {
  return this.$el;
};

PlacementsRowEditor.prototype.setMemoryInputValue = function(valueBytes, selector) {
  if (valueBytes && utils.isValidNonNegativeIntValue(valueBytes)) {
    let size = formatUtils.calculateMemorySize(valueBytes);

    this.$el.find(selector + ' input').val(size.value);
    this.$el.find(selector + ' select').val(size.unit);
  } else {
    this.$el.find(selector + ' input').val('');
    this.$el.find(selector + ' select').val('MB');
  }
};

PlacementsRowEditor.prototype.getMemoryInputValue = function(selector) {
  var memoryLimitVal = this.$el.find(selector + ' input').val();
  var memoryLimitUnit = this.$el.find(selector + ' select').val();

  if (memoryLimitVal && utils.isValidNonNegativeIntValue(memoryLimitVal)) {
    let bytesValue = formatUtils.toBytes(memoryLimitVal, memoryLimitUnit);
    if (utils.isValidNonNegativeIntValue(bytesValue)) {
      return bytesValue;
    }
  }

  return null;
};

PlacementsRowEditor.prototype.setData = function(data) {
  if (this.data !== data) {
    var oldData = this.data || {};

    if (oldData.item !== data.item) {
      var placementObject = data.item;

      if (placementObject && placementObject.documentSelfLink) {
        this.$el.find('.title').html(i18n.t('app.placement.edit.update'));
      } else {
        this.$el.find('.title').html(i18n.t('app.placement.edit.createNew'));
        placementObject = {};
      }

      let groupInputValue = placementObject.group ? placementObject.group.id :
        placementObject.groupId;
      this.placementGroupInput.setValue(groupInputValue);
      this.placementZoneInput.setSelectedOption(placementObject.placementZone);
      if (this.deploymentPolicyInput) {
        this.deploymentPolicyInput.setSelectedOption(placementObject.deploymentPolicy);
      }
      this.$el.find('.maxInstancesInput input').val(placementObject.maxNumberInstances);
      this.$el.find('.priorityInput input').val(placementObject.priority);
      this.$el.find('.nameInput input').val(placementObject.name);

      this.setMemoryInputValue(placementObject.memoryLimit, '.memoryLimitInput');
    }

    if (data.placementZones === constants.LOADING) {
      this.placementZoneInput.setLoading(true);
    } else {
      this.placementZoneInput.setLoading(false);
      this.placementZoneInput.setOptions(
          (data.placementZones || []).map((config) => config.resourcePoolState));
    }

    if (oldData.selectedPlacementZone !== data.selectedPlacementZone &&
        data.selectedPlacementZone) {
      this.placementZoneInput.setSelectedOption(data.selectedPlacementZone.resourcePoolState);
    }

    // todo add loading for groups input
    this.placementGroupInput.setItems(data.groups);

    let oldSelectedGroup = oldData.item ? oldData.item.group : oldData.group;
    let selectedGroup = data.selectedGroup;
    if (!selectedGroup) {
      selectedGroup = data.item ? data.item.group : data.group;
    }
    if (oldSelectedGroup !== selectedGroup && selectedGroup) {
      let placementGroupValue = selectedGroup.id ? selectedGroup.id : selectedGroup.name;
      if (!placementGroupValue) {
        placementGroupValue = selectedGroup;
      }
      this.placementGroupInput.setValue(placementGroupValue);
    }

    if (data.deploymentPolicies === constants.LOADING) {
      this.deploymentPolicyInput.setLoading(true);
    } else {
      this.deploymentPolicyInput.setLoading(false);
      this.deploymentPolicyInput.setOptions(data.deploymentPolicies);
    }

    if (oldData.selectedDeploymentPolicy !== data.selectedDeploymentPolicy &&
      data.selectedDeploymentPolicy) {
      this.deploymentPolicyInput.setSelectedOption(data.selectedDeploymentPolicy);
    }

    if (oldData.validationErrors !== data.validationErrors) {
      updateAlert.call(this, this.$el, data.validationErrors);
    }

    toggleButtonsState.call(this);

    this.data = data;
  }
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.inline-edit-holder').on('click', '.inline-edit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = getPlacementModel.call(_this);

    if (toReturn.documentSelfLink) {
      PlacementActions.updatePlacement(toReturn);
    } else {
      PlacementActions.createPlacement(toReturn);
    }
  });

  this.$el.find('.inline-edit-holder').on('click', '.inline-edit-cancel', function(e) {
    e.preventDefault();
    PlacementActions.cancelEditPlacement();
  });

  this.$el.on('change input', function() {
    toggleButtonsState.call(_this);
  });
};

var getPlacementModel = function() {
  var toReturn = {};
  if (this.data.item && this.data.item.documentSelfLink) {
    $.extend(toReturn, this.data.item);
  }

  toReturn.name = validator.trim(this.$el.find('.nameInput input').val());

  toReturn.groupId = this.placementGroupInput.getValue();
  toReturn.placementZone = this.placementZoneInput.getSelectedOption();
  toReturn.deploymentPolicy = this.deploymentPolicyInput.getSelectedOption();

  var maxNumberInstances = this.$el.find('.maxInstancesInput input').val();
  if ($.isNumeric(maxNumberInstances) && utils.isValidNonNegativeIntValue(maxNumberInstances)) {
    toReturn.maxNumberInstances = maxNumberInstances;
  } else if (maxNumberInstances === '') {
    toReturn.maxNumberInstances = 0;
  }

  var priority = this.$el.find('.priorityInput input').val();
  if ($.isNumeric(priority) && utils.isValidNonNegativeIntValue(priority)) {
      toReturn.priority = priority;
  }

  toReturn.memoryLimit = this.getMemoryInputValue('.memoryLimitInput');

  return toReturn;
};

var toggleButtonsState = function() {

  var placementZone = this.placementZoneInput.getSelectedOption();
  var name = validator.trim(this.$el.find('.nameInput input').val());
  var priority = this.$el.find('.priorityInput input').val();
  var maxNumberInstances = this.$el.find('.maxInstancesInput input').val();
  var memoryLimit = this.$el.find('.memoryLimitInput input').val();

  var $verifyBtn = this.$el.find('.inline-edit-verify');
  $verifyBtn.removeClass('loading');

  var $saveBtn = this.$el.find('.inline-edit-save');
  $saveBtn.removeClass('loading');

  var groupClause = !utils.isApplicationEmbedded() || this.placementGroupInput.getValue();
  var priorityClause = !priority || utils.isValidNonNegativeIntValue(priority);
  utils.applyValidationError(this.$el.find('.priorityInput'),
                             priorityClause ? null : i18n.t('errors.invalidInputValue'));
  var maxNumberInstancesClause = !maxNumberInstances
                                    || utils.isValidNonNegativeIntValue(maxNumberInstances);
  utils.applyValidationError(this.$el.find('.maxInstancesInput'),
                             maxNumberInstancesClause ? null : i18n.t('errors.invalidInputValue'));

  var memoryLimitClause = !memoryLimit || (this.getMemoryInputValue('.memoryLimitInput') !== null);
  utils.applyValidationError(this.$el.find('.memoryLimitInput'),
                                    memoryLimitClause ? null : i18n.t('errors.invalidInputValue'));

  let notEnoughInfo = !placementZone || !name || !priorityClause || !maxNumberInstancesClause
                      || !groupClause || !memoryLimitClause;
  if (notEnoughInfo) {
    $saveBtn.attr('disabled', true);
  } else {
    $saveBtn.removeAttr('disabled');
  }
};

var updateAlert = function($el, errors) {
  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors && errors._generic);
};

/* An adapter that renders group or business group input based on the application type */
class GroupInput {
  constructor($containerEl, valueChangeCallback) {

    var $groupEl = $('<div>', {
      class: 'form-control dropdown-holder'
    });
    $containerEl.append($groupEl);

    if (utils.isApplicationEmbedded()) {

      this.businessGroupInput = new DropdownSearchMenu($groupEl, {
        title: i18n.t('app.placement.edit.selectBusinessGroup'),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.businessGroup.entity')
        })
      });

      this.businessGroupInput.setOptionSelectCallback(valueChangeCallback);
      this.businessGroupInput.setClearOptionSelectCallback(valueChangeCallback);

    } else {

      this.groupInput = new DropdownSearchMenu($groupEl, {
        title: i18n.t('app.placement.edit.selectGroup'),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.group.entity')
        })
      });

      // Hide 'Manage' and 'New' option if projects are not displayed
      // in the context toolbar
      if (!ft.showProjectsInNavigation()) {
        this.groupInput.setManageOptions(GROUPS_MANAGE_OPTIONS);
      }

      this.groupInput.setManageOptionSelectCallback(function(option) {
        if (option.id === 'group-create') {
          PlacementContextToolbarActions.createResourceGroup();
        } else {
          PlacementContextToolbarActions.manageResourceGroups();
        }
      });

      this.groupInput.setOptionSelectCallback(valueChangeCallback);
      this.groupInput.setClearOptionSelectCallback(valueChangeCallback);
    }
  }

  getValue() {
    let input = (this.groupInput) ? (this.groupInput) : this.businessGroupInput;
    let selectedGroupInstance = input.getSelectedOption();

    return selectedGroupInstance ? selectedGroupInstance.id : null;
  }

  setValue(groupId) {
    this.groupId = groupId;
    this.setGroupInstanceIfNeeded();
  }

  setItems(groups) {
    let input = (this.groupInput) ? (this.groupInput) : this.businessGroupInput;

    var adaptedGroups = [];
    if (groups) {
      for (var i = 0; i < groups.length; i++) {
        let group = groups[i];
        var groupInstance = {
          id: group.id ? group.id : utils.getDocumentId(group.documentSelfLink),
          name: group.label ? group.label : group.name

        };
        adaptedGroups.push(groupInstance);
      }
    }

    this.adaptedGroups = adaptedGroups;
    input.setOptions(adaptedGroups);

    this.setGroupInstanceIfNeeded();
  }

  setGroupInstanceIfNeeded() {
    let input = (this.groupInput) ? (this.groupInput) : this.businessGroupInput;

    if (this.groupId) {
      let selectedGroupInstance = this.adaptedGroups && this.adaptedGroups.find((groupInstance) => {
          return groupInstance.id === this.groupId || groupInstance.name === this.groupId;
        });

      if (selectedGroupInstance) {
        input.setSelectedOption(selectedGroupInstance);
      } else {
        input.setSelectedOption({
          id: this.groupId,
          name: this.groupId
        });
      }
    } else if (input) {
      input.setSelectedOption(null);
    }
  }
}

export default PlacementsRowEditor;
