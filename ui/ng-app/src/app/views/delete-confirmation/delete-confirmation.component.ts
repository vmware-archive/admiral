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

@Component({
  selector: 'delete-confirmation',
  templateUrl: './delete-confirmation.component.html',
  styleUrls: ['./delete-confirmation.component.scss']
})
/**
 * Modal delete confirmation
 */
export class DeleteConfirmationComponent {

  @Input() title: string;
  @Input() description: string;

  show: boolean = false;
  @Input() get visible() : boolean {
      return this.show;
  }
  set visible (value: boolean) {
      this.show = value;
      this.isDeleting = false;
  }

  @Output() alertChange: EventEmitter<string> = new EventEmitter();
  @Output() onDelete: EventEmitter<any> = new EventEmitter();
  @Output() onCancel: EventEmitter<any> = new EventEmitter();

  modalOpened: boolean;
  isDeleting: boolean;
  alertMessage: string;

  @Input() get alert(): string {
    return this.alertMessage;
  }

  set alert(alert: string) {
    this.alertMessage = alert;
    if (this.alert) {
      this.isDeleting = false;
    }
    this.alertChange.emit(this.alertMessage);
  }

  resetAlert() {
    this.alert = null;
  }

  deleteConfirmed() {
    this.resetAlert();
    this.isDeleting = true;
    this.onDelete.emit(null);
  }

  deleteCanceled() {
    this.resetAlert();
    this.onCancel.emit(null);
  }

}
