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

import CredentialsRowEditTemplate from 'components/credentials/CredentialsRowEditTemplate.html';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import Alert from 'components/common/Alert';
import constants from 'core/constants';
import utils from 'core/utils';
import { CredentialsActions } from 'actions/Actions';

function CredentialsRowEditor() {
  this.$el = $(CredentialsRowEditTemplate());

  this.alert = new Alert(this.$el, this.$el.find('.inline-edit'), false);

  this.customProperties = new MulticolumnInputs(this.$el.find('.custom-properties'), {
    name: {
      header: i18n.t('customProperties.name'),
      placeholder: i18n.t('customProperties.nameHint')
    },
    value: {
      header: i18n.t('customProperties.value'),
      placeholder: i18n.t('customProperties.valueHint')
    }
  });

  addEventListeners.call(this);
}

CredentialsRowEditor.prototype.getEl = function() {
  return this.$el;
};

CredentialsRowEditor.prototype.setData = function(data) {
  this.credentialsObject = data.item;

  this.$el.find('.name-input').val('').removeAttr('disabled');

  // Fix for css navigation transition glitch
  // https://bugs.chromium.org/p/chromium/issues/detail?id=97367
  setTimeout(() => {
    this.$el.find('.name-input').focus();
  }, 310);

  this.$el.find('.username-input').val('');
  this.$el.find('.password-input').val('');
  this.$el.find('.showPassword').addClass('hide');
  this.$el.find('.usePrivateKey').removeClass('hide');
  this.$el.find('.password-input-shown').val('');
  this.$el.find('.password-input-holder').removeClass('hide');
  this.$el.find('.password-input-shown-holder').addClass('hide');
  this.$el.find('.private-key-input-holder').addClass('hide');
  this.$el.find('.private-key-input').val('');

  this.$el.find('.public-certificate-input').val('');
  this.$el.find('.private-certificate-input').val('');

  // Checked by default
  this.$el.find('#credentialTypePassword')[0].checked = true;
  this.$el.find('#credentialTypeCertificate')[0].checked = false;
  this.$el.find('#credentialTypePublic')[0].checked = false;

  this.$el.find('.inline-edit-save').removeClass('loading').removeAttr('disabled');

  if (this.credentialsObject) {
    this.$el.find('.title').html(i18n.t('app.credential.edit.update'));
    this.$el.find('.name-input').val(this.credentialsObject.name).attr('disabled', true);

    if (this.credentialsObject.type === constants.CREDENTIALS_TYPE.PASSWORD
      || this.credentialsObject.type === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
      this.$el.find('.username-input').val(this.credentialsObject.username);

      let usePassword = this.credentialsObject.type === constants.CREDENTIALS_TYPE.PASSWORD;
      if (usePassword) {
        this.$el.find('.private-key-input-holder').addClass('hide');

        this.$el.find('.usePrivateKey').addClass('hide');
        this.$el.find('.showPassword').removeClass('hide');
        this.$el.find('.password-input').val(this.credentialsObject.password);
        this.$el.find('.password-input-holder').removeClass('hide');
      } else {
        this.$el.find('.password-input-holder').addClass('hide');
        this.$el.find('.private-key-input').val(
                          utils.maskValueIfEncrypted(this.credentialsObject.privateKey));
        this.$el.find('.private-key-input-holder').removeClass('hide');
      }

    } else if (this.credentialsObject.type === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
      this.$el.find('#credentialTypeCertificate')[0].checked = true;
      this.$el.find('#credentialTypePassword')[0].checked = false;
      this.$el.find('.public-certificate-input').val(this.credentialsObject.publicKey);
      this.$el.find('.private-certificate-input').val(
                          utils.maskValueIfEncrypted(this.credentialsObject.privateKey));
    } else {
      this.$el.find('#credentialTypePublic')[0].checked = true;
      this.$el.find('#credentialTypePassword')[0].checked = false;
      this.$el.find('.public-input').val(this.credentialsObject.publicKey);
    }

    handleCredentialsTypeInputs(this.$el, this.credentialsObject.type);

    this.customProperties.setData(utils.objectToArray(this.credentialsObject.customProperties));
  } else {
    this.$el.find('.title').html(i18n.t('app.credential.edit.createNew'));
    handleCredentialsTypeInputs(this.$el, constants.CREDENTIALS_TYPE.PASSWORD);

    this.customProperties.setData(null);
  }

  applyValidationErrors.call(this, this.$el, data.validationErrors);

  toggleButtonsState(this.$el);
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.inline-edit')
    .on('change', 'input:radio[name="credentialsType"]', function() {
      var credentialsType = _this
        .$el.find('.inline-edit input:radio[name="credentialsType"]:checked').val();
      handleCredentialsTypeInputs(_this.$el, credentialsType);
      toggleButtonsState(_this.$el);
    });

  this.$el.find('.inline-edit').on('click', '.inline-edit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = {};

    if (_this.credentialsObject) {
      $.extend(toReturn, _this.credentialsObject);
    } else {
      toReturn.name = validator.trim(_this.$el.find('.name-input').val());
    }

    var credentialsType = _this
      .$el.find('.inline-edit input:radio[name="credentialsType"]:checked').val();
    if (credentialsType === constants.CREDENTIALS_TYPE.PASSWORD) {
      toReturn.username = validator.trim(_this.$el.find('.username-input').val());
      let usePassword = _this.$el.find('.private-key-input-holder').hasClass('hide');

      if (usePassword) {
        toReturn.type = constants.CREDENTIALS_TYPE.PASSWORD;
        toReturn.password = getPasswordValue(_this.$el);
      } else {
        toReturn.type = constants.CREDENTIALS_TYPE.PRIVATE_KEY;
        toReturn.privateKey = utils.unmaskValueIfEncrypted(
                                      validator.trim(_this.$el.find('.private-key-input').val()),
                                      toReturn.privateKey);
      }
    } else if (credentialsType === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
      toReturn.type = constants.CREDENTIALS_TYPE.PUBLIC_KEY;
      toReturn.publicKey = validator.trim(_this.$el.find('.public-certificate-input').val());
      toReturn.privateKey = utils.unmaskValueIfEncrypted(
                                      validator.trim(
                                        _this.$el.find('.private-certificate-input').val()),
                                      toReturn.privateKey);
    } else {
      toReturn.type = constants.CREDENTIALS_TYPE.PUBLIC;
      toReturn.publicKey = validator.trim(_this.$el.find('.public-input').val());
    }

    toReturn.customProperties = utils.arrayToObject(_this.customProperties.getData());

    if (_this.credentialsObject) {
      CredentialsActions.updateCredential(toReturn);
    } else {
      CredentialsActions.createCredential(toReturn);
    }
  });

  this.$el.find('.inline-edit').on('click', '.inline-edit-cancel', function(e) {
    e.preventDefault();
    _this.credentialsObject = null;

    CredentialsActions.cancelEditCredential();
  });

  this.$el.find('.usePrivateKey').click(function(e) {
    e.preventDefault();

    _this.$el.find('.password-input-holder').addClass('hide');
    _this.$el.find('.password-input-shown-holder').addClass('hide');
    _this.$el.find('.private-key-input-holder').removeClass('hide');

    _this.$el.find('.private-key-input').focus();
  });

  this.$el.find('.usePassword').click(function(e) {
    e.preventDefault();

    _this.$el.find('.private-key-input-holder').addClass('hide');
    _this.$el.find('.password-input-holder').removeClass('hide');

    _this.$el.find('.password-input').focus();
  });

  this.$el.find('.showPassword').click(function(e) {
    e.preventDefault();

    _this.$el.find('.password-input-holder').addClass('hide');
    _this.$el.find('.password-input-shown-holder').removeClass('hide');

    _this.$el.find('.password-input-shown').val(_this.$el.find('.password-input').val()).focus();
  });

  this.$el.find('.hidePassword').click(function(e) {
    e.preventDefault();

    _this.$el.find('.password-input-shown-holder').addClass('hide');
    _this.$el.find('.password-input-holder').removeClass('hide');

    _this.$el.find('.password-input').val(_this.$el.find('.password-input-shown').val()).focus();
  });

  this.$el.on('change input', '.password-input, .password-input-shown', function(e) {
    var password = $(e.target).val();
    if (password) {
      _this.$el.find('.usePrivateKey').addClass('hide');
      _this.$el.find('.showPassword').removeClass('hide');
      _this.$el.find('.hidePassword').removeClass('hide');
    } else {
      _this.$el.find('.usePrivateKey').removeClass('hide');
      _this.$el.find('.showPassword').addClass('hide');
      _this.$el.find('.hidePassword').addClass('hide');
    }

    toggleButtonsState(_this.$el);
  });

  this.$el.on('change input', '.name-input, .username-input, ' +
              '.private-key-input, ' +
              '.public-certificate-input, .private-certificate-input, ' +
              '.public-input',
    function() {
      toggleButtonsState(_this.$el);
  });
};

var handleCredentialsTypeInputs = function($el, type) {
  $el.find('.inline-edit-passwordInputs')
    .toggleClass('hide', type !== constants.CREDENTIALS_TYPE.PASSWORD
                           && type !== constants.CREDENTIALS_TYPE.PRIVATE_KEY);

  $el.find('.inline-edit-certificateInputs')
    .toggleClass('hide', type !== constants.CREDENTIALS_TYPE.PUBLIC_KEY);

  $el.find('.inline-edit-publicInputs')
    .toggleClass('hide', type !== constants.CREDENTIALS_TYPE.PUBLIC);
};

var applyValidationErrors = function($el, errors) {
  errors = errors || {};

  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors._generic);
};

var toggleButtonsState = function($el) {
  let nameValue = $el.find('.name-input').val();

  if (!nameValue) {
    $el.find('.inline-edit-save').attr('disabled', true);
    return;
  }

  let credentialsType = $el.find('.inline-edit input:radio[name="credentialsType"]:checked')
    .val();
  if (credentialsType === constants.CREDENTIALS_TYPE.PASSWORD) {
    let userName = $el.find('.username-input').val();
    let usePassword = $el.find('.private-key-input-holder').hasClass('hide');

    if (usePassword) {
      let password = getPasswordValue($el);
      toggleSaveButtonState($el, userName && password);
    } else {
      let privateKey = $el.find('.private-key-input').val();
      toggleSaveButtonState($el, userName && privateKey);
    }
  } else if (credentialsType === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    let publicKey = $el.find('.public-certificate-input').val();
    let privateKey = $el.find('.private-certificate-input').val();

    toggleSaveButtonState($el, publicKey && privateKey);
  } else {
    let publicKey = $el.find('.public-input').val();

    toggleSaveButtonState($el, publicKey);
  }
};

var toggleSaveButtonState = function($el, enableCondition) {
  if (enableCondition) {
    $el.find('.inline-edit-save').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.inline-edit-save').attr('disabled', true);
  }
};

var getPasswordValue = function($el) {
  var isPasswordShown = $el.find('.password-input-holder').hasClass('hide');
  if (isPasswordShown) {
    return validator.trim($el.find('.password-input-shown').val());
  } else {
    return validator.trim($el.find('.password-input').val());
  }
};

export default CredentialsRowEditor;
