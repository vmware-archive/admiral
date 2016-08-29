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

import { CertificatesActions } from 'actions/Actions';
import services from 'core/services';
import utils from 'core/utils';
import constants from 'core/constants';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'list'
};

let CertificatesStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [CertificatesActions],

  onRetrieveCertificates: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['items'], constants.LOADING);
      this.emitChange();

      operation.forPromise(services.loadCertificates()).then((result) => {
        var certificates = [];
        for (var key in result) {
          if (result.hasOwnProperty(key)) {
            certificates.push(result[key]);
          }
        }

        this.setInData(['items'], certificates);
        this.emitChange();
      });
    }
  },

  onEditCertificate: function(certificate) {
    this.setInData(['editingItemData', 'item'], certificate);
    this.emitChange();
  },

  onCancelEditCertificate: function() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onCreateCertificate: function(certificate) {
    services.createCertificate(certificate).then((createdCertificate) => {
      var immutableCertificate = Immutable(createdCertificate);

      var certificates = this.data.items.asMutable();
      certificates.push(immutableCertificate);

      this.setInData(['items'], certificates);
      this.setInData(['newItem'], immutableCertificate);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['newItem'], null);
    }).catch(this.onGenericEditError);
  },

  onUpdateCertificate: function(certificate) {
    services.updateCertificate(certificate).then((updatedCertificate) => {
      // If the backend did not make any changes, the response will be empty
      updatedCertificate = updatedCertificate || certificate;
      var immutableCertificate = Immutable(updatedCertificate);

      var certificates = this.data.items.asMutable();

      for (var i = 0; i < certificates.length; i++) {
        if (certificates[i].documentSelfLink === immutableCertificate.documentSelfLink) {
          certificates[i] = immutableCertificate;
        }
      }

      this.setInData(['items'], certificates);
      this.setInData(['updatedItem'], immutableCertificate);
      this.setInData(['editingItemData'], null);
      this.emitChange();

      // After we notify listeners, the new item is no logner actual
      this.setInData(['updatedItem'], null);
    }).catch(this.onGenericEditError);
  },

  onDeleteCertificate: function(certificate) {
    services.deleteCertificate(certificate).then(() => {
      var certificates = this.data.items.filter(
        (c) => c.documentSelfLink !== certificate.documentSelfLink);

      this.setInData(['items'], certificates);
      this.emitChange();
    });
  },

  onGenericEditError: function(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    // console.error(e);
    this.emitChange();
  }
});

export default CertificatesStore;
