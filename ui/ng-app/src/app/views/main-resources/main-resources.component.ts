import { NavigationEnd } from '@angular/router';
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
import { Router } from '@angular/router';

@Component({
  selector: 'app-main-resources',
  templateUrl: './main-resources.component.html',
  styleUrls: ['./main-resources.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class MainResourcesComponent implements OnInit, OnDestroy {

    kubernetesEnabled = FT.isKubernetesHostOptionEnabled();

    embeddedMode = FT.isApplicationEmbedded();

    routeObserve: Subscription;

    formerViewPaths = {
      'templates': 'templates?$category=templates',
      'public-repositories': 'templates?$category=images',
      'registries': 'registries',
      'hosts': 'hosts',
      'applications': 'applications',
      'containers': 'containers',
      'networks': 'networks',
      'volumes': 'volumes'
    }

    formerViewPath;

    constructor(private router: Router) { }

    ngOnInit() {
      this.routeObserve = this.router.events.subscribe((event) => {
        if (event instanceof NavigationEnd) {
          let formerViewPath;
          if (event.urlAfterRedirects.startsWith("/home/")) {
            let url = event.urlAfterRedirects.replace("/home/", "");
            for (let key in this.formerViewPaths) {
              if (url.startsWith(key)) {
                formerViewPath = this.formerViewPaths[key]
              }
            }
          }

          this.formerViewPath = formerViewPath;
        }
      });
    }

    ngOnDestroy() {
      this.routeObserve.unsubscribe();
    }
}
