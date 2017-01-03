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

import Alert from 'components/common/Alert';
import { DeploymentPolicyActions } from 'actions/Actions';
import DeploymentPoliciesRowEditTemplate from
  'components/deploymentpolicies/DeploymentPoliciesRowEditTemplate.html';
import constants from 'core/constants';

function DeploymentPoliciesRowEditor() {
  this.$el = $(DeploymentPoliciesRowEditTemplate());
  this.$el.find('.fa-question-circle').tooltip();
  this.alert = new Alert(this.$el, this.$el.find('.inline-edit'), false);

  addEventListeners.call(this);
}

DeploymentPoliciesRowEditor.prototype.getEl = function() {
  return this.$el;
};

DeploymentPoliciesRowEditor.prototype.setData = function(data) {
  this.deploymentPolicy = data.item;

  this.$el.find('.inline-edit-save').removeAttr('disabled').removeClass('loading');

  if (this.deploymentPolicy) {
    this.$el.find('.title').html(i18n.t('app.deploymentPolicy.edit.update'));
    this.$el.find('.name-input').val(this.deploymentPolicy.name);
    this.$el.find('.description-input').val(this.deploymentPolicy.description);
  } else {
    this.$el.find('.title').html(i18n.t('app.deploymentPolicy.edit.createNew'));
    this.$el.find('.name-input').val('');
    this.$el.find('.description-input').val('');
  }

  this.$el.find('.name-input').first().focus();

  applyValidationErrors.call(this, this.$el, data.validationErrors);

  toggleButtonsState(this.$el);

  this.data = data;
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.inline-edit').on('click', '.inline-edit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var policy = {};

    if (_this.deploymentPolicy) {
      $.extend(policy, _this.deploymentPolicy);
    }

    policy.name = _this.$el.find('.name-input').val();
    policy.description = _this.$el.find('.description-input').val();

    if (_this.deploymentPolicy) {
      DeploymentPolicyActions.updateDeploymentPolicy(policy);
    } else {
      DeploymentPolicyActions.createDeploymentPolicy(policy);
    }
  });

  this.$el.find('.inline-edit').on('click', '.inline-edit-cancel', function(e) {
    e.preventDefault();
    _this.deploymentPolicy = null;
    DeploymentPolicyActions.cancelEditDeploymentPolicy();
  });

  this.$el.find('.name-input').on('input change', function() {
    toggleButtonsState(_this.$el);
  });
};

var applyValidationErrors = function($el, errors) {
  errors = errors || {};

  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors._generic);
};

var toggleButtonsState = function($el) {
  var nameValue = $el.find('.name-input').val();
  if (nameValue) {
    $el.find('.inline-edit-save').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.inline-edit-save').attr('disabled', true);
  }
};

export default DeploymentPoliciesRowEditor;
