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

import { Component, AfterViewInit, OnChanges, Input, ViewChild, Pipe, PipeTransform, ViewEncapsulation } from '@angular/core';
import { AuthService } from './../../utils/auth.service';
import { FT } from './../../utils/ft';

@Component({
  selector: 'login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class LoginComponent {

  hasError = false;
  isVic = FT.isVic();
  username: string;
  password: string;
  loading = false;

  constructor(private authService: AuthService) { }

  login() {
    this.username = this.username ? this.username.trim() : '';
    this.password = this.password ? this.password.trim() : '';
    this.hasError = false;
    this.loading = true;

    this.authService.login(this.username, this.password).then(() => {
      this.loading = false;
      window.location.reload();
    }, (e) => {
      this.loading = false;
      this.hasError = true;
    });
  }
}


