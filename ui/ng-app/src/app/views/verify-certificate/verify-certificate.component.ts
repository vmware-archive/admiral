/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, EventEmitter, Input, Output } from '@angular/core';
import * as I18n from 'i18next';

@Component({
  selector: 'verify-certificate',
  templateUrl: './verify-certificate.component.html',
  styleUrls: ['./verify-certificate.component.scss']
})
/**
 * Modal verify untrusted certificate
 */
export class VerifyCertificateComponent {

  @Input() visible: boolean;
  @Input() certificate: any;
  @Input() hostAddress: string;

  certificateShown: boolean = false;

  @Output() onAccept: EventEmitter<any> = new EventEmitter();
  @Output() onDecline: EventEmitter<any> = new EventEmitter();

  warningMessage() {
    if (this.certificate) {
      return I18n.t("certificate.certificateWarning", { address: this.hostAddress } as I18n.TranslationOptions); }
    return '';
  }

  showCertificate() {
    this.certificateShown = true;
  }

  hideCertificate() {
    this.certificateShown = false;
  }

  declineCertificate() {
    this.onDecline.emit(null);
  }

  acceptCertificate() {
    this.onAccept.emit(null);
  }
}
