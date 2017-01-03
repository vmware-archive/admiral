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

import CertificatesRowEditTemplate from
  'components/certificates/CertificatesRowEditTemplate.html';
import Alert from 'components/common/Alert';
import ImportedCertificatesStore from 'stores/ImportedCertificatesStore';
import { CertificatesActions } from 'actions/Actions';
import constants from 'core/constants';

function CertificatesRowEditor() {
  this.$el = $(CertificatesRowEditTemplate());
  this.$el.find('.fa-question-circle').tooltip();
  this.alert = new Alert(this.$el, this.$el.find('.inline-edit'), false);

  addEventListeners.call(this);
}

CertificatesRowEditor.prototype.getEl = function() {
  return this.$el;
};

CertificatesRowEditor.prototype.setData = function(data) {
  this.certificateHolder = data.item;

  this.$el.find('.uri-input').val('');
  this.$el.find('.certificate-import-option').removeClass('hide');
  this.$el.find('.certificate-import-inputs').addClass('hide');
  this.$el.find('.certificate-import-button').removeClass('loading').removeAttr('disabled');
  this.$el.find('.inline-edit-save').removeClass('loading').removeAttr('disabled');
  this.$el.find('.certificate-input').removeAttr('readonly');

  if (this.certificateHolder) {
    this.$el.find('.certificate-input').val(this.certificateHolder.certificate);
    this.$el.find('.title').html(i18n.t('app.certificate.edit.update'));
  } else {
    this.$el.find('.certificate-input').val('');
    this.$el.find('.title').html(i18n.t('app.certificate.edit.createNew'));
  }

  applyValidationErrors.call(this, this.$el, data.validationErrors);
  toggleButtonsState(this.$el);
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.inline-edit').on('click', '.inline-edit-save', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    var toReturn = {};

    if (_this.certificateHolder) {
      $.extend(toReturn, _this.certificateHolder);
    }

    toReturn.certificate = validator.trim(_this.$el.find('.certificate-input').val());

    if (_this.certificateHolder) {
      CertificatesActions.updateCertificate(toReturn);
    } else {
      CertificatesActions.createCertificate(toReturn);
    }
  });

  this.$el.find('.inline-edit').on('click', '.inline-edit-cancel', function(e) {
    e.preventDefault();
    _this.certificateHolder = null;
    CertificatesActions.cancelEditCertificate();
  });

  this.$el.find('.certificate-import-option-toggle').click(function(e) {
    e.preventDefault();

    _this.$el.find('.certificate-import-option').addClass('hide');
    _this.$el.find('.certificate-import-inputs').removeClass('hide');
    _this.$el.find('.certificate-input').val('').attr('readonly', true);
    _this.$el.find('.uri-input').focus();

    toggleButtonsState(_this.$el);
  });

  this.$el.find('.certificate-import-option-cancel').click(function(e) {
    e.preventDefault();

    _this.$el.find('.certificate-import-option').removeClass('hide');
    _this.$el.find('.certificate-import-inputs').addClass('hide');
    _this.$el.find('.certificate-input').removeAttr('readonly');

    toggleButtonsState(_this.$el);
  });

  this.$el.find('.certificate-import-button').click(function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');

    CertificatesActions.importCertificate(_this.$el.find('.uri-input').val());
  });

  this.$el.find('.certificate-input').on('input change', function() {
    toggleButtonsState(_this.$el);
  });

  this.$el.find('.uri-input').on('input change', function() {
    toggleButtonsState(_this.$el);
  });

  ImportedCertificatesStore.listen(function(importedCertificateInfo) {
    var currentUri = _this.$el.find('.uri-input').val();

    if (importedCertificateInfo.hostUri === currentUri) {
      if (importedCertificateInfo.certificateHolder) {
        _this.$el.find('.certificate-input').val(
          importedCertificateInfo.certificateHolder.certificate);

        toggleButtonsState(_this.$el);
      } else {
        _this.$el.find('.certificate-input').val('');
      }

      _this.alert.toggle(_this.$el, constants.ALERTS.TYPE.FAIL, importedCertificateInfo.error);

      _this.$el.find('.certificate-import-button').removeClass('loading');
    }
  });
};

var applyValidationErrors = function($el, errors) {
  errors = errors || {};

  this.alert.toggle($el, constants.ALERTS.TYPE.FAIL, errors._generic);
};

var toggleButtonsState = function($el) {
  var certificateValue = $el.find('.certificate-input').val();
  if (certificateValue) {
    $el.find('.inline-edit-save').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.inline-edit-save').attr('disabled', true);
  }

  var uriValue = $el.find('.uri-input').val();
  if (uriValue) {
    $el.find('.certificate-import-button').removeAttr('disabled').removeClass('loading');
  } else {
    $el.find('.certificate-import-button').attr('disabled', true);
  }
};

export default CertificatesRowEditor;
