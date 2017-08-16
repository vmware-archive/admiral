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

import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ErrorService } from '../../utils/error.service';
import { RoutesRestriction } from '../../utils/routes-restriction';
import { FT } from './../../utils/ft';

@Component({
  selector: 'app-administration',
  templateUrl: './administration.component.html',
  styleUrls: ['./administration.component.scss']
})
/**
 * Administration of the application.
 */
export class AdministrationComponent implements OnInit {

  errorObserve: Subscription;

  alertMessage: string;
  isHbrEnabled = FT.isHbrEnabled();

  constructor(private errorService: ErrorService) {

      this.errorObserve = this.errorService.errorMessages.subscribe((event) => {
          this.alertMessage = event;
      });
  }

  ngOnInit() {
  }

  ngOnDestroy() {
    this.errorObserve.unsubscribe();
  }

  get identityManagementRouteRestriction() {
    return RoutesRestriction.IDENTITY_MANAGEMENT;
  }

  get projectsRouteRestriction() {
    return RoutesRestriction.PROJECTS;
  }

  get registriesRouteRestriction() {
    return RoutesRestriction.REGISTRIES;
  }

  get configurationRouteRestriction() {
    return RoutesRestriction.CONFIGURATION;
  }

  get logsRouteRestriction() {
    return RoutesRestriction.LOGS;
  }

  resetAlert() {
    this.alertMessage = null;
  }
}
