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

import { OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { containsTree } from '@angular/router/src/url_tree';
import { DocumentService } from '../../utils/document.service';

export class BaseListComponent implements OnInit {
  protected entities = [];
  protected loadingEntities = false;

  constructor(protected service: DocumentService, protected router: Router, protected link: string) {}

  ngOnInit() {
    this.loadingEntities = true;
    this.service.list(this.link).then(result => {
      this.entities = result;
      this.loadingEntities = false;
    });
  }

  isRouteActive(route) {
    const currentUrlTree = this.router.parseUrl(this.router.url);
    const routeUrlTree = this.router.createUrlTree([route]);
    return containsTree(currentUrlTree, routeUrlTree, true);
  }
}
