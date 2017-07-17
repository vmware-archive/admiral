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

import { Pipe, PipeTransform } from '@angular/core';
import * as I18n from 'i18next';

@Pipe({ name: 'severityText' })
/**
 * Pipe converting severity to translatable string.
 */
export class SeverityTextPipe implements PipeTransform {

    public transform(severityConst: any): string {
        if (severityConst) {
            switch (severityConst) {
                case 'INFO':
                    return I18n.t('logs.event.type.info');
                case 'WARNING':
                    return I18n.t('logs.event.type.warning');
                case 'ERROR':
                    return I18n.t('logs.event.type.error');
                default:
                    return I18n.t('logs.event.type.info');
            }
        }

        return I18n.t('logs.event.type.info');
    }
}
