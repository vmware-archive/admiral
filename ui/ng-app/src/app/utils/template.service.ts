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

import { Injectable } from '@angular/core';
import { Ajax } from './ajax.service';
import { Links } from './links';
import { URLSearchParams, RequestOptions, ResponseContentType, Headers } from '@angular/http';
import { searchConstants, serviceUtils} from 'admiral-ui-common';

@Injectable()
export class TemplateService {

  constructor(private ajax: Ajax) { }

  public importContainerTemplate(template) {
    let headers = new Headers()
    headers.append('Accept', 'application/yaml, */*; q=0.01');
    headers.append('X-Requested-With', 'XMLHttpRequest');
    headers.append('Content-Type', 'application/yaml');

    let requestOptions = new RequestOptions({
      url: Links.COMPOSITE_DESCRIPTIONS_CONTENT,
      body: template,
      method: 'POST',
      headers: headers,
      responseType: ResponseContentType.Text,
      withCredentials: true
    });

    return this.ajax.ajaxRaw(requestOptions)
      .then(result => {
        return result.headers.get('location');
      });
  };
}