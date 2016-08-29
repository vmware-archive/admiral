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

import PoliciesRowEditTemplate from 'PoliciesRowEditTemplate';
import {
  PolicyContextToolbarActions, PolicyActions
}
from 'actions/Actions';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Alert from 'components/common/Alert';
import constants from 'core/constants';
import utils from 'core/utils';

const resourcePoolManageOptions = [{
  id: 'rp-create',
  name: i18n.t('app.resourcePool.createNew'),
  icon: 'plus'
}, {
  id: 'rp-manage',
  name: i18n.t('app.resourcePool.manage'),
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

function PoliciesRowEditor() {
  this.$el = $(PoliciesRowEditTemplate());

  if (utils.isApplicationEmbedded()) {
    let groupFromEl = this.$el.find('.group');
    let groupFormLabelEl = groupFromEl.find('.control-label');
    groupFormLabelEl.append('<span class="requiredFieldMark">*</span>');
  }

  this.$el.find('.fa-question-circle').tooltip();

  this.alert = new Alert(this.$el, this.$el.find('.policyEdit'), true);

  this.policyGroupInput = new GroupInput(this.$el.find('.policyGroupInput'), () =>
    toggleButtonsState.call(this));

  let resourcePoolInputEl = this.$el.find('.resourcePool .form-control');
  this.resourcePoolInput = new DropdownSearchMenu(resourcePoolInputEl, {
    title: i18n.t('app.policy.edit.selectResourcePool'),
    searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
      entity: i18n.t('app.resourcePool.entity')
    })
  });

  this.resourcePoolInput.setManageOptions(resourcePoolManageOptions);
  this.resourcePoolInput.setManageOptionSelectCallback(function(option) {
    if (option.id === 'rp-create') {
      PolicyContextToolbarActions.createResourcePool();
    } else {
      PolicyContextToolbarActions.manageResourcePools();
    }
  });
  this.resourcePoolInput.setClearOptionSelectCallback(() => toggleButtonsState.call(this));

  this.resourcePoolInput.setOptionSelectCallback(() => toggleButtonsState.call(this));

  var deploymentPolicyEl = this.$el.find('.deploymentPolicy .form-control');
  this.deploymentPolicyInput = new DropdownSearchMenu(deploymentPolicyEl, {
    title: i18n.t('app.policy.edit.selectDeploymentPolicy'),
    searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
      entity: i18n.t('app.deploymentPolicy.entity')
    })
  });

  this.deploymentPolicyInput.setManageOptions(deploymentPolicyManageOptions);
  this.deploymentPolicyInput.setManageOptionSelectCallback(function(option) {
    if (option.id === 'policy-create') {
      PolicyContextToolbarActions.createDeploymentPolicy();
    } else {
      PolicyContextToolbarActions.manageDeploymentPolicies();
    }
  });

  this.deploymentPolicyInput.setOptionSelectCallback(() => toggleButtonsState.call(this));

  addEventListeners.call(this);
}

PoliciesRowEditor.prototype.getEl = function() {
  return this.$el;
};

PoliciesRowEditor.prototype.setData = function(data) {
  if (this.data !== data) {
    var oldData = this.data || {};

    if (oldData.item !== data.item) {
      var policyObject = data.item;

      if (policyObject && policyObject.documentSelfLink) {
        this.$el.find('.title').html(i18n.t('app.policy.edit.update'));
      } else {
        this.$el.find('.title').html(i18n.t('app.policy.edit.createNew'));
        policyObject = {};
      }

      let groupInputValue = policyObject.group ? policyObject.group.id : policyObject.groupId;
      this.policyGroupInput.setValue(groupInputValue);
      this.resourcePoolInput.setSelectedOption(policyObject.resourcePool);
      this.deploymentPolicyInput.setSelectedOption(policyObject.deploymentPolicy);
      this.$el.find('.maxInstancesInput input').val(policyObject.maxNumberInstances);
      this.$el.find('.priorityInput input').val(policyObject.priority);
      this.$el.find('.nameInput input').val(policyObject.name);

      if ($.isNumeric(policyObject.memoryLimit)) {
        let size = utils.fromBytes(policyObject.memoryLimit);
        normalizeToKB(size);

        this.$el.find('.memoryLimitInput input').val(size.value);
        this.$el.find('.memoryLimitInput select').val(size.unit);
      } else {
        this.$el.find('.memoryLimitInput input').val('');
        this.$el.find('.memoryLimitInput select').val('MB');
      }

      if ($.isNumeric(policyObject.storageLimit)) {
        let size = utils.fromBytes(policyObject.storageLimit);
        normalizeToKB(size);

        this.$el.find('.storageLimitInput input').val(size.value);
        this.$el.find('.storageLimitInput select').val(size.unit);
      } else {
        this.$el.find('.storageLimitInput input').val('');
        this.$el.find('.storageLimitInput select').val('MB');
      }

      this.$el.find('.cpuSharesInput input').val(policyObject.cpuShares);
    }

    if (data.resourcePools === constants.LOADING) {
      this.resourcePoolInput.setLoading(true);
    } else {
      this.resourcePoolInput.setLoading(false);
      this.resourcePoolInput.setOptions(data.resourcePools);
    }

    if (oldData.selectedResourcePool !== data.selectedResourcePool && data.selectedResourcePool) {
      this.resourcePoolInput.setSelectedOption(data.selectedResourcePool);
    }

    // todo add loading for groups input
    this.policyGroupInput.setItems(data.groups);

    let oldSelectedGroup = oldData.item ? oldData.item.group : oldData.group;
    let selectedGroup = data.selectedGroup;
    if (!selectedGroup) {
      selectedGroup = data.item ? data.item.group : data.group;
    }
    if (oldSelectedGroup !== selectedGroup && selectedGroup) {
      this.policyGroupInput.setValue(selectedGroup.id ? selectedGroup.id : selectedGroup.name);
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

  this.$el.find('.policyEditHolder').on('click', '.policyRowEdit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = getPolicyModel.call(_this);

    if (toReturn.documentSelfLink) {
      PolicyActions.updatePolicy(toReturn);
    } else {
      PolicyActions.createPolicy(toReturn);
    }
  });

  this.$el.find('.policyEditHolder').on('click', '.policyRowEdit-cancel', function(e) {
    e.preventDefault();
    PolicyActions.cancelEditPolicy();
  });

  this.$el.on('change input', function() {
    toggleButtonsState.call(_this);
  });
};

var getPolicyModel = function() {
  var toReturn = {};
  if (this.data.item && this.data.item.documentSelfLink) {
    $.extend(toReturn, this.data.item);
  }

  toReturn.name = this.$el.find('.nameInput input').val();

  toReturn.groupId = this.policyGroupInput.getValue();
  toReturn.resourcePool = this.resourcePoolInput.getSelectedOption();
  toReturn.deploymentPolicy = this.deploymentPolicyInput.getSelectedOption();

  var maxNumberInstances = this.$el.find('.maxInstancesInput input').val();
  if ($.isNumeric(maxNumberInstances)) {
    toReturn.maxNumberInstances = maxNumberInstances;
  } else if (maxNumberInstances === '') {
    toReturn.maxNumberInstances = 0;
  }

  var priority = this.$el.find('.priorityInput input').val();
  if ($.isNumeric(priority)) {
    toReturn.priority = priority;
  }

  var memoryLimitVal = this.$el.find('.memoryLimitInput input').val();
  var memoryLimitUnit = this.$el.find('.memoryLimitInput select').val();
  if ($.isNumeric(memoryLimitVal)) {
    toReturn.memoryLimit = utils.toBytes(memoryLimitVal, memoryLimitUnit);
  }

  var cpuLimitVal = this.$el.find('.storageLimitInput input').val();
  var cpuLimitUnit = this.$el.find('.storageLimitInput select').val();
  if ($.isNumeric(cpuLimitVal)) {
    toReturn.storageLimit = utils.toBytes(cpuLimitVal, cpuLimitUnit);
  }

  var cpuShares = this.$el.find('.cpuSharesInput input').val();
  if ($.isNumeric(cpuShares)) {
    toReturn.cpuShares = cpuShares;
  }
  return toReturn;
};

var toggleButtonsState = function() {

  var resourcePool = this.resourcePoolInput.getSelectedOption();
  var maxNumberInstances = this.$el.find('.maxInstancesInput input').val();
  var memoryLimit = this.$el.find('.memoryLimitInput input').val();
  var cpuShares = this.$el.find('.cpuSharesInput input').val();

  var $verifyBtn = this.$el.find('.policyRowEdit-verify');
  $verifyBtn.removeClass('loading');

  var $saveBtn = this.$el.find('.policyRowEdit-save');
  $saveBtn.removeClass('loading');

  var groupClause = !utils.isApplicationEmbedded() || this.policyGroupInput.getValue();
  var maxNumberInstancesClause = !maxNumberInstances || $.isNumeric(maxNumberInstances)
                                                          && parseInt(maxNumberInstances, 10) >= 0;
  var memoryLimitClause = !memoryLimit
                            || $.isNumeric(memoryLimit) && parseInt(memoryLimit, 10) >= 0;
  var cpuSharesClause = !cpuShares
                            || $.isNumeric(cpuShares) && parseInt(cpuShares, 10) >= 0;

  let notEnoughInfo = !resourcePool || !maxNumberInstancesClause || !groupClause
                        || !memoryLimitClause || !cpuSharesClause;
  if (notEnoughInfo) {
    $saveBtn.attr('disabled', true);
  } else {
    $saveBtn.removeAttr('disabled');
  }
};

var updateAlert = function($el, errors) {
  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors && errors._generic);
};

var normalizeToKB = function(size) {
  if (size.unit === 'Bytes') {
    size.value /= 1000;
    size.unit = 'kB';
  }
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
        title: i18n.t('app.policy.edit.selectBusinessGroup'),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.businessGroup.entity')
        })
      });

      this.businessGroupInput.setOptionSelectCallback(valueChangeCallback);
      this.businessGroupInput.setClearOptionSelectCallback(valueChangeCallback);

    } else {

      this.groupInput = new DropdownSearchMenu($groupEl, {
        title: i18n.t('app.policy.edit.selectGroup'),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.group.entity')
        })
      });

      this.groupInput.setManageOptions(GROUPS_MANAGE_OPTIONS);
      this.groupInput.setManageOptionSelectCallback(function(option) {
        if (option.id === 'group-create') {
          PolicyContextToolbarActions.createResourceGroup();
        } else {
          PolicyContextToolbarActions.manageResourceGroups();
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

export default PoliciesRowEditor;
