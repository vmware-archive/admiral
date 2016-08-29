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

let ImportedCertificatesStore = Reflux.createStore({
  listenables: [CertificatesActions],

  onImportCertificate: function(hostUri, acceptCertificate) {
    var certificateImportInfo = {
      hostUri: hostUri
    };

    services.importCertificate(hostUri, acceptCertificate)
    .then((certificateHolder, location) => {
      if (certificateHolder) {
        certificateImportInfo.certificateHolder = certificateHolder;
        certificateImportInfo.certificateNeedsAcceptance = true;
        this.trigger(certificateImportInfo);
      } else if (location) {
        services.loadCertificate(location)
        .then((loadedCertificateHolder) => {
          certificateImportInfo.certificateHolder = loadedCertificateHolder;
          this.trigger(certificateImportInfo);
        });
      } else {
        certificateImportInfo.isKnownCertificate = true;
        this.trigger(certificateImportInfo);
      }
    }).catch((e) => {
      certificateImportInfo.error = e.responseJSON.message;
      this.trigger(certificateImportInfo);
    });
  }
});

export default ImportedCertificatesStore;
