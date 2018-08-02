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

import { AbstractControl } from "@angular/forms";
import { Utils } from "./utils";

 export class CustomValidators {

    public static validateUrl(control: AbstractControl): {[key: string]: boolean} | null {
        var url = control.value;
        if (url) {
            var urlParts = Utils.getURLParts(url);
            if (urlParts.scheme && urlParts.host) {
                return null;
            }
        }

        return { 'url': true };
    }
 }