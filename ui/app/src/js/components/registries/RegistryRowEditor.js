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

import RegistryRowEditTemplate from 'RegistryRowEditTemplate';
import { RegistryContextToolbarActions, RegistryActions } from 'actions/Actions';
import HostCertificateConfirmTemplate from 'HostCertificateConfirmTemplate';
import Alert from 'components/common/Alert';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import constants from 'core/constants';
import modal from 'core/modal';
import utils from 'core/utils';

const credentialManageOptions = [
  {
    id: 'cred-create',
    name: i18n.t('app.credential.createNew'),
    icon: 'plus'
  },
  {
    id: 'cred-manage',
    name: i18n.t('app.credential.manage'),
    icon: 'pencil'
  }
];

function RegistryRowEditor() {
  this.$el = $(RegistryRowEditTemplate());

  this.alert = new Alert(this.$el, this.$el.find('.registryEdit'));

  this.credentialInput = new DropdownSearchMenu(this.$el.find('#credential .form-control'), {
    title: i18n.t('dropdownSearchMenu.title', {
      entity: i18n.t('app.credential.entity')
    }),
    searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
      entity: i18n.t('app.credential.entity')
    })
  });

  this.credentialInput.setManageOptions(credentialManageOptions);
  this.credentialInput.setManageOptionSelectCallback(function(option) {
    if (option.id === 'cred-create') {
      RegistryContextToolbarActions.createCredential();
    } else {
      RegistryContextToolbarActions.manageCredentials();
    }
  });

  this.$el.find('.fa-question-circle').tooltip({html: true});

  this.credentialInput.setOptionSelectCallback(() => toggleButtonsState(this.$el));

  addEventListeners.call(this);
}

RegistryRowEditor.prototype.getEl = function() {
  return this.$el;
};

RegistryRowEditor.prototype.setData = function(data) {
  if (this.data !== data) {
    var oldData = this.data || {};

    if (oldData.item !== data.item) {
      var registryObject = data.item;

      if (registryObject) {
        this.$el.find('#hostname input').val(registryObject.address);
        this.$el.find('#name input').val(registryObject.name);
        this.credentialInput.setSelectedOption(registryObject.credential);
      } else {
        this.$el.find('#hostname input').val('');
        this.$el.find('#name input').val('');
        this.credentialInput.setSelectedOption(null);
      }
    }

    this.credentialInput.setOptions(data.credentials);
    if (oldData.selectedCredential !== data.selectedCredential && data.selectedCredential) {
      this.credentialInput.setSelectedOption(data.selectedCredential);
    }

    if (oldData.validationErrors !== data.validationErrors) {
      updateAlert.call(this, this.$el, data.validationErrors);
    }

    if (oldData.shouldAcceptCertificate !== data.shouldAcceptCertificate) {
      updateCetificateModal.call(this, data.shouldAcceptCertificate);
    }

    toggleButtonsState(this.$el);

    this.data = data;
  }
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.registryEditHolder').on('click', '.registryRowEdit-verify', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = getRegistryModel.call(_this);
    RegistryActions.verifyRegistry(toReturn);
  });

  this.$el.find('.registryEditHolder #hostname input').focusout(function(e) {
    e.preventDefault();

    var hostnameInput = $(e.currentTarget);
    hostnameInput.val(utils.populateDefaultSchemeAndPort(hostnameInput.val()));
  });

  this.$el.find('.registryEditHolder').on('click', '.registryRowEdit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = getRegistryModel.call(_this);

    if (toReturn.documentSelfLink) {
      RegistryActions.updateRegistry(toReturn);
    } else {
      RegistryActions.createRegistry(toReturn);
    }
  });

  this.$el.find('.registryEditHolder').on('click', '.registryRowEdit-cancel', function(e) {
    e.preventDefault();
    RegistryActions.cancelEditRegistry();
  });

  var hostnameInput = this.$el.find('#hostname input');
  hostnameInput.on('change input', function() {
    var hostname = hostnameInput.val();
    RegistryActions.checkInsecureRegistry(hostname);
  });

  this.$el.on('change input', function() {
    toggleButtonsState(_this.$el);
  });
};

var getRegistryModel = function() {
  var toReturn = {};
  if (this.data.item && this.data.item.documentSelfLink) {
    $.extend(toReturn, this.data.item);
  }

  toReturn.address = this.$el.find('#hostname input').val();
  toReturn.name = this.$el.find('#name input').val();
  toReturn.credential = this.credentialInput.getSelectedOption();

  return toReturn;
};

var updateAlert = function($el, errors) {
  var alertMessage;
  var alertType;

  if (errors) {
    if (errors._generic) {
      alertMessage = errors._generic;
      alertType = constants.ALERTS.TYPE.FAIL;
    } else if (errors._valid) {
      alertMessage = i18n.t('app.registry.edit.verified');
      alertType = constants.ALERTS.TYPE.SUCCESS;
    } else if (errors._insecure) {
      alertMessage = i18n.t('app.registry.edit.insecureRegistryHint');
      alertType = constants.ALERTS.TYPE.WARNING;
    }
  }

  this.alert.toggle($el, alertType, alertMessage);
};

var toggleButtonsState = function($el) {
  var address = $el.find('#hostname input').val();
  var name = $el.find('#name input').val();

  var $verifyBtn = $el.find('.registryRowEdit-verify');
  $verifyBtn.removeClass('loading');

  var $saveBtn = $el.find('.registryRowEdit-save');
  $saveBtn.removeClass('loading');

  if (!address || !name) {
    $saveBtn.attr('disabled', true);
  } else {
    $saveBtn.removeAttr('disabled');
  }
};

var updateCetificateModal = function(shouldAcceptCertificate) {
  if (shouldAcceptCertificate) {
    createAndShowCertificateConfirm.call(this, shouldAcceptCertificate.certificateHolder,
                                         shouldAcceptCertificate.verify);
  } else {
    modal.hide();
  }
};

var createAndShowCertificateConfirm = function(certificateHolder, isVerify) {
  var $certificateConfirm = $(HostCertificateConfirmTemplate(certificateHolder));
  modal.show($certificateConfirm);

  var certificateWarning = i18n.t('app.host.details.certificateWarning',
                                  {address: this.$el.find('#hostname input').val()});
  $certificateConfirm.find('.certificate-warning-text').html(certificateWarning);

  $certificateConfirm.find('.manage-certificates-button').click(function(e) {
    e.preventDefault();
    RegistryContextToolbarActions.manageCertificates();
    modal.hide();
  });

  $certificateConfirm.find('.show-certificate-btn').click(function(e) {
    e.preventDefault();
    $certificateConfirm.addClass('active');
  });

  $certificateConfirm.find('.hide-certificate-btn').click(function(e) {
    e.preventDefault();
    $certificateConfirm.removeClass('active');
  });

  var _this = this;
  $certificateConfirm.find('.confirmAddHost').click(function(e) {
    e.preventDefault();
    var registryModel = getRegistryModel.call(_this);
    $(e.currentTarget).addClass('loading');
    if (isVerify) {
      RegistryActions.acceptCertificateAndVerifyRegistry(certificateHolder, registryModel);
    } else {
      RegistryActions.acceptCertificateAndCreateRegistry(certificateHolder, registryModel);
    }
  });

  $certificateConfirm.find('.confirmCancel').click(function(e) {
    e.preventDefault();
    modal.hide();
  });
};

export default RegistryRowEditor;
