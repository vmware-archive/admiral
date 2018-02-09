/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DocumentService } from '../../utils/document.service';
import { Subscription } from "rxjs/Subscription";

/**
 * Base view for a single entity details.
 */
export class BaseDetailsComponent implements OnInit, OnDestroy {
  id;
  entity;
  protected projectLink: string;

  private routeParamsSubscription: Subscription = null;
  private routeParentParamsSubscription: Subscription = null;

  constructor(protected route: ActivatedRoute, protected service: DocumentService,
              protected link: string) {
  }

  protected entityInitialized() {
      // add logic when entity details are loaded.
  }

  ngOnInit() {
    this.routeParamsSubscription = this.route.params.subscribe(params => {
       this.id = params['id'];

       if (!this.id) {
           // no need to retrieve data
         return;
       }

       this.service.getById(this.link, this.id, this.projectLink).then(service => {
         this.entity = service;
         this.entityInitialized();
       });
    });

    // try with the parent
    this.routeParentParamsSubscription = this.route.parent.params.subscribe(params => {
        this.id = params['id'];

        if (!this.id) {
            // no need to retrieve data
            return;
        }

        this.service.getById(this.link, this.id, this.projectLink).then(service => {
            this.entity = service;
            this.entityInitialized();
        });
    });
  }

  ngOnDestroy() {
    this.routeParamsSubscription.unsubscribe();
    this.routeParentParamsSubscription.unsubscribe();
  }
}
