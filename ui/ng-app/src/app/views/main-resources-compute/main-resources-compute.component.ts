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

import { FT } from './../../utils/ft';
import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Router, NavigationEnd } from '@angular/router';

@Component({
  selector: 'app-main-resources-compute',
  templateUrl: './main-resources-compute.component.html',
  styleUrls: ['./main-resources-compute.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComputeComponent implements OnInit, OnDestroy {

    embeddedMode = FT.isApplicationEmbedded();
    isProfilesSplitEnabled = FT.isProfilesSplitEnabled();

    routeObserve: Subscription;

    formerViewPaths = {
      'endpoints': 'endpoints',
      'compute': 'compute',
      'profiles': 'profiles',
      'placements': 'placements',
      'placementZones': 'placementZones',
      'machines': 'machines',
      'networks': 'networks',
      'instance-types/new': 'instance-types/new',
      'instance-types/edit': 'instance-types/edit'
    }

    formerViewPath;

    constructor(private router: Router) { }

    ngOnInit() {
      this.routeObserve = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) {
          if (event.urlAfterRedirects.startsWith("/compute/")) {
            let url = event.urlAfterRedirects.replace("/compute/", "");
            for (let key in this.formerViewPaths) {
              if (url.startsWith(key)) {
                this.formerViewPath = url.replace(key, this.formerViewPaths[key]);
                return;
              }
            }

            this.formerViewPath = null;
          }
        }
      });
    }

    ngOnDestroy() {
      this.routeObserve.unsubscribe();
    }
}
