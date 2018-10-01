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

import { Injectable } from '@angular/core';
import { HttpHeaders, HttpParams, HttpRequest } from '@angular/common/http';
import { AjaxService } from './ajax.service';
import { Links } from './links';
import { Utils } from './utils';

@Injectable()
export class TemplateService {

    constructor(private ajax: AjaxService) {
    }

    public importContainerTemplate(template) {
        let headers = new HttpHeaders({
            'Accept': 'application/yaml, */*; q=0.01',
            'X-Requested-With': 'XMLHttpRequest',
            'Content-Type': 'application/yaml'
        });

        let request = new HttpRequest('POST',
            Utils.serviceUrl(Links.COMPOSITE_DESCRIPTIONS_CONTENT),
            template, {
            headers: headers,
            reportProgress: false,
            params: new HttpParams(),
            responseType: 'text',
            withCredentials: true
        });

        return this.ajax.ajaxRaw(request).then(result => {
            return result.headers.get('location');
        });
    };
}
