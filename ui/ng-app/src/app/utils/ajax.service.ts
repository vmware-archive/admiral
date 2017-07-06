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
import { Http, RequestOptions, Headers, ResponseContentType, Request, URLSearchParams, RequestMethod }
  from '@angular/http';
import { Subject } from './subject';

import 'rxjs/add/operator/toPromise';

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

@Injectable()
export class Ajax {
  constructor(private http: Http, private sessionTimedOutSubject: SessionTimedOutSubject) { }

  /**
   * ajax: makes a request with the appropriate proxy.
   *
   * @param {String} method for the request
   * @param {String} the url to call
   * @param {String} params querystring params for the request
   * @param {String} data POST data for the request
   * @return {Object} promise got the call
   */
  private ajax(
    method: string | RequestMethod,
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {

    //Xenon wants to see string is hte body, turning data to string if needed
    if (data && typeof data !== 'string') {
      data = JSON.stringify(data);
    }

    headers.append('Accept', 'application/json, */*; q=0.01');
    headers.append('X-Requested-With', 'XMLHttpRequest');
    headers.append('Content-Type', 'application/json');

    let requestOptions = new RequestOptions({
      url: this.serviceUrl(url),
      body: data,
      method: method,
      headers: headers,
      responseType: ResponseContentType.Json,
      search: params,
      withCredentials: true
    });

    return this.http.request(new Request(requestOptions))
      .toPromise()
      .then(result => this.trimMetadata(result.json()))
      .catch(error => this.handleAjaxError(error));
  }

  /**
   * Trims metadata out from the result payload.
   *
   * Basically, it only returns the 'content' attribute if present in the result payload.
   *
   * @param  {Object} result    the result payload from the API call
   * @return {Object}           the payload trimmed to only its content
   */
  private trimMetadata(result) {
    if (result && result.content) {
      return result.content;
    } else {
      return result;
    }
  }

  /**
   * handleAjaxErrors
   *
   * @param  {type} errors description
   * @return {type}        description
   */
  private handleAjaxError(error) {
    // handle session timeout
    if (error && error.status && error.status === 401) {
      this.sessionTimedOutSubject.setError(error);
      throw error; // propagate the initial error
    }

    throw error; // propagate the initial error
  }

  // tslint:disable-next-line: no-reserved-keywords
  public get(
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {
    return this.ajax(RequestMethod.Get, url, params, data, headers);
  }

  public put(
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {
    return this.ajax(RequestMethod.Put, url, params, data, headers);
  }

  public post(
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {
    return this.ajax(RequestMethod.Post, url, params, data, headers);
  }

  // tslint:disable-next-line: no-reserved-keywords
  public delete(
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {
    return this.ajax(RequestMethod.Delete, url, params, data, headers);
  }

  public patch(
    url: string,
    params = new URLSearchParams(),
    data = {},
    headers = new Headers()): Promise<any> {
    return this.ajax(RequestMethod.Patch, url, params, data, headers);
  }

  public ajaxRaw(requestOptions: RequestOptions): Promise<any> {
    requestOptions.url = this.serviceUrl(requestOptions.url);

    return this.http.request(new Request(requestOptions))
      .toPromise()
      .catch(error => this.handleAjaxError(error));
  }

  /**
   * Polls a query task url until it's finished, failed or cancelled
   *
   * @param  {String} pollUrl      The URL to poll
   * @param  {Number} interval The interval in between polls, defaults to 500ms
   *
   * @return {Promise}          The promise that is resolved with the poll results
   * if the poll finishes or rejected otherwise with an error or the failed queryResult
   */
  public pollQueryUrl(pollUrl: string, interval = 500): Promise<any> {
    return new Promise((resolve, reject) => {
      let pollId;
      let pollQuery = (url) => {
        this.get(url).then(queryResult => {
          if (!queryResult || !queryResult.taskInfo) {
            console.warn('Unknown query poll response', queryResult);
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
              console.warn('Unknown query task stage, aborting', queryResult.taskInfo.stage);
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
      pollId = setInterval(
        () => {
          pollQuery(pollUrl);
        },
        interval);
    });
  }

  // It could be set on App level to send calls to a different base.
  private serviceUrl(path) {
    let wnd:any = window;
    if (wnd.getBaseServiceUrl) {
      return wnd.getBaseServiceUrl(path);
    }
    return path;
  };
}
