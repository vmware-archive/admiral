/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    selector: 'credentials-select',
    templateUrl: './credentials-select.component.html',
    styleUrls: ['./credentials-select.component.scss']
})
/**
 * Credentials selection component.
 */
export class CredentialsSelectComponent {
    @Input() credentials: any[];
    @Input() credentialsLoading: boolean;
    @Input() selected: any;
    @Input() selectDataName: string = 'credentials-select';
    @Input() styleShort: boolean = false;

    @Output() onSelect: EventEmitter<any> = new EventEmitter();

    credentialsGroup: FormGroup = new FormGroup({
        credentials: new FormControl()
    });

    get hasCredentials() : boolean {
        return this.credentials && this.credentials.length > 0;
    }

    onSelectionChange($event) {
        let credentialsSelection = $event.target.value !== '' ? $event.target.value : null;

        this.onSelect.emit(credentialsSelection);
    }
}
