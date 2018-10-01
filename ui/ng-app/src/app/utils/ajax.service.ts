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
import {HttpClient, HttpHeaders, HttpParams, HttpRequest, HttpResponse} from '@angular/common/http';
import { Subject} from './subject';
import { Utils } from './utils';

@Injectable()
export class SessionTimedOutSubject extends Subject<Object> {
    private error: Error | Object;

    public setError(error) {
        this.error = error;
        this.emit();
    }

    public getModel() {
        return {
            error: this.error
        };
    }
}

/**
 * Service performing ajax calls.
 */
@Injectable()
export class AjaxService {

    constructor(private http: HttpClient,
                private sessionTimedOutSubject: SessionTimedOutSubject) {
        //
    }

    /**
     * ajax: makes a request with the appropriate proxy.
     */
    private ajax(method: string, url: string, params: any = {}, data = {},
                 headers: any = {}): Promise<any> {

        // Xenon wants to see string in the body, turning data to string if needed
        if (data && typeof data !== 'string') {
            data = JSON.stringify(data);
        }

        headers['Accept'] = 'application/json, */*; q=0.01';
        headers['X-Requested-With'] = 'XMLHttpRequest';
        headers['Content-Type'] = 'application/json';

        let httpParams = new HttpParams({ fromObject: params });
        let httpHeaders = new HttpHeaders(headers);

        let request = new HttpRequest(method, Utils.serviceUrl(url), data, {
            headers: httpHeaders,
            reportProgress: false,
            params: httpParams,
            responseType: 'json',
            withCredentials: true
        });

        return this.http.request(request).toPromise()
                            .then((result: HttpResponse<any>) => {
                                return result && result.body;
                            })
                            .catch(error => {
                                this.handleAjaxError(error)
                            });
    }

    private handleAjaxError(error) {
        let status = error && error.status;

        // Handle session timeout
        if (status === 401 || status === 403) {
            this.sessionTimedOutSubject.setError(error);
            // propagate the initial error
            throw error;
        }
        // propagate the initial error
        throw error;
    }

    public get(url: string, params: any = {}, data = {},
               headers: any = {}): Promise<any> {
        return this.ajax('GET', url, params, data, headers);
    }

    public put(url: string, params: any = {}, data = {},
               headers: any = {}): Promise<any> {
        return this.ajax('PUT', url, params, data, headers);
    }

    public post(url: string, params: any = {}, data = {},
                headers: any = {}): Promise<any> {
        return this.ajax('POST', url, params, data, headers);
    }

    public delete(url: string, params: any = {}, data = {},
                  headers: any = {}): Promise<any> {
        return this.ajax('DELETE', url, params, data, headers);
    }

    public patch(url: string, params: any = {}, data = {},
                 headers: any = {}): Promise<any> {
        return this.ajax('PATCH', url, params, data, headers);
    }

    /**
     * Make sure url is Utils.serviceUrl(url).
     *
     * @param {HttpRequest<any>} request
     * @returns {Promise<any>}
     */
    public ajaxRaw(request: HttpRequest<any>): Promise<any> {
        return this.http.request(request)
                            .toPromise()
                            .catch(error => this.handleAjaxError(error));
    }

    /**
     * Polls a query task url until it's finished, failed or cancelled.
     */
    public pollQueryUrl(pollUrl: string, interval = 500): Promise<any> {

        return new Promise((resolve, reject) => {
            let pollId;

            let pollQuery = (url) => {
                this.get(url).then(queryResult => {
                    if (!queryResult || !queryResult.taskInfo) {
                        console.warn('Unknown query poll response',
                            queryResult);

                        clearInterval(pollId);
                        reject(queryResult);
                        return;
                    }

                    switch (queryResult.taskInfo.stage) {
                        case 'FAILED':
                        case 'CANCELLED':
                            clearInterval(pollId);
                            reject(queryResult);
                            break;
                        case 'FINISHED':
                            clearInterval(pollId);
                            resolve(queryResult);
                            break;
                        default:
                            console.warn('Unknown query task stage, aborting',
                                queryResult.taskInfo.stage);

                            clearInterval(pollId);
                            reject(queryResult);
                            break;
                    }
                }).catch(error => {
                    console.error('Unknown error while polling query url, aborting', error);

                    clearInterval(pollId);
                    reject(error);
                });
            };

            pollId = setInterval(() => {
                pollQuery(pollUrl);
                }, interval);
        });
    }
}
