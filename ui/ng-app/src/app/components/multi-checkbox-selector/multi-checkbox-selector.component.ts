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

import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';

@Component({
    selector: 'multi-checkbox-selector',
    templateUrl: './multi-checkbox-selector.component.html',
    styleUrls: ['./multi-checkbox-selector.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
/**
 * Component allowing multiple selection of items, using checkboxes.
 */
export class MultiCheckboxSelectorComponent implements OnChanges {
    /**
     * An option's data is in format:
     * {
     *   name: string,
     *   value: string,
     *   checked: boolean
     * }.
     */
    @Input() options: any[];

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.options) {
            if (!this.options.find(option => !option.checked)) {
                this.allSelected = true;
            }
        }
    }

    // all options selection
    private _isAllSelected: boolean = false;

    set allSelected(value: boolean) {
        this._isAllSelected = value;

        if (this._isAllSelected) {
            this.options.forEach(option => option.checked = true);
        }
    }

    get allSelected(): boolean {
        return this._isAllSelected;
    }

    /**
     * @returns {any[]} the selected options. Each option is in format:
     * {
     *   name: string,
     *   value: string,
     *   checked: boolean
     * }.
     */
    get selectedOptions(): any[] {
        if (this.allSelected) {
            return this.options;
        } else {
            return this.options.filter((option => option.checked))
        }
    }
}
