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

import CertificatesList from 'components/certificates/CertificatesList'; //eslint-disable-line
import EndpointsViewVue from 'components/endpoints/EndpointsViewVue.html';
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import { EndpointContextToolbarActions, EndpointsActions } from 'actions/Actions';

export default Vue.component('endpoints-view', {
  template: EndpointsViewVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  computed: {
    certificateWarning() {
      const address = this.model.editingItemData.item.endpointProperties.host ||
          this.model.editingItemData.item.endpointProperties.hostName ||
          this.model.editingItemData.item.endpointProperties.regionId;
      return i18n.t('app.host.details.certificateWarning', {
        address: address,
        interpolation: {
          // Vue.js double mustache syntax will do the escaping
          escapeValue: false
        }
      });
    },
    activeContextItem() {
      return this.model.editingItemData && this.model.editingItemData.contextView &&
          this.model.editingItemData.contextView.activeItem &&
          this.model.editingItemData.contextView.activeItem.name;
    },
    contextExpanded() {
      return this.model.editingItemData && this.model.editingItemData.contextView &&
          this.model.editingItemData.contextView.expanded;
    }
  },
  data() {
    return {
      certificateDetails: false
    };
  },
  methods: {
    showCertificate($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      this.certificateDetails = true;
    },
    hideCertificate($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      this.certificateDetails = false;
    },
    acceptVerification($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      this.certificateDetails = false;
      EndpointsActions.acceptVerifyEndpoint();
    },
    cancelVerification($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      this.certificateDetails = false;
      EndpointsActions.cancelVerifyEndpoint();
    },
    manageCertificates($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();
      this.certificateDetails = false;
      EndpointContextToolbarActions.manageCertificates();
    },
    openToolbarCertificates: EndpointContextToolbarActions.openToolbarCertificates,
    closeToolbar: EndpointContextToolbarActions.closeToolbar
  }
});
