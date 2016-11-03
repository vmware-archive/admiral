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

import ResourceGroupsRowEditTemplate from 'ResourceGroupsRowEditTemplate';
import Alert from 'components/common/Alert';
import { ResourceGroupsActions } from 'actions/Actions';
import constants from 'core/constants';

function ResourceGroupsRowEditor() {
  this.$el = $(ResourceGroupsRowEditTemplate());
  this.$el.find('.fa-question-circle').tooltip();
  this.alert = new Alert(this.$el, this.$el.find('.resourceGroupEdit-header'));

  addEventListeners.call(this);
}

ResourceGroupsRowEditor.prototype.getEl = function() {
  return this.$el;
};

ResourceGroupsRowEditor.prototype.setData = function(data) {
  this.group = data.item;

  this.$el.find('.resourceGroupEdit-save').removeAttr('disabled').removeClass('loading');

  if (this.group) {
    this.$el.find('.title').html(i18n.t('app.group.edit.update'));
    this.$el.find('.name-input').val(this.group.name);

  } else {
    this.$el.find('.title').html(i18n.t('app.group.edit.createNew'));
    this.$el.find('.name-input').val('');
  }

  this.$el.find('.name-input').first().focus();

  applyValidationErrors.call(this, this.$el, data.validationErrors);

  toggleButtonsState(this.$el);

  this.data = data;
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.resourceGroupEdit').on('click', '.resourceGroupEdit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var group = {};

    if (_this.group) {
      $.extend(group, _this.group);
    }

    group.name = validator.trim(_this.$el.find('.name-input').val());

    if (_this.group) {
      ResourceGroupsActions.updateGroup(group);
    } else {
      ResourceGroupsActions.createGroup(group);
    }
  });

  this.$el.find('.resourceGroupEdit').on('click', '.resourceGroupEdit-cancel', function(e) {
    e.preventDefault();
    _this.group = null;
    ResourceGroupsActions.cancelEditGroup();
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
    $el.find('.resourceGroupEdit-save').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.resourceGroupEdit-save').attr('disabled', true);
  }
};

export default ResourceGroupsRowEditor;
