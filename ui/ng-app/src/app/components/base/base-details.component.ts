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
import { Subscription } from "rxjs/Subscription";
import { DocumentService } from '../../utils/document.service';
import { ErrorService } from "../../utils/error.service";
import { Utils } from "../../utils/utils";

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
              protected link: string, protected errorService: ErrorService = null) {
  }

  protected entityInitialized() {
      // add logic when entity details are loaded.
  }

  protected routeParamsReceived(params) {
    // do something with the route params.
  }

  ngOnInit() {
    this.routeParamsSubscription = this.route.params.subscribe(params => {
       this.id = params['id'];

       if (!this.id) {
           // no need to retrieve data
           this.routeParamsReceived(params);
           return;
       }

       this.service.getById(this.link, this.id, this.projectLink).then(service => {
           this.entity = service;
           this.entityInitialized();

       }).catch(error => {
           console.error('Failed loading entity ', error);

           if (this.errorService) {
               this.errorService.error(Utils.getErrorMessage(error)._generic);
           }
       });

       this.routeParamsReceived(params);
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

        }).catch(error => {
            console.error('Failed loading parent entity ', error);

            if (this.errorService) {
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            }
        });
    });
  }

  ngOnDestroy() {
    this.routeParamsSubscription.unsubscribe();
    this.routeParentParamsSubscription.unsubscribe();
  }
}
