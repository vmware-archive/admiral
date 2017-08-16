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

import { Injectable } from "@angular/core";
import { Subject } from 'rxjs/Subject';
import { ErrorHandler } from "harbor-ui";

/**
 * Service for propagating error messages.
 */
@Injectable()
export class ErrorService extends ErrorHandler {

    private _errorMessages = new Subject<string>();
    private _warningMessages = new Subject<string>();
    private _infoMessages = new Subject<string>();
    private _logMessages = new Subject<string>();

    errorMessages = this._errorMessages.asObservable();
    warningMessages = this._warningMessages.asObservable();
    infoMessages = this._infoMessages.asObservable();
    logMessages = this._logMessages.asObservable();

    public error(error: any): void {
        console.error("error", error);

        this._errorMessages.next(error);
    }

    public warning(warning: any): void {
        console.warn("warning", warning);

        this._warningMessages.next(warning);
    }

    public info(info: any): void {
        console.info("info: ", info);

        this._infoMessages.next(info);
    }

    public log(log: any): void {
        console.log("log: ", log);

        this._logMessages.next(log);
    }
}
