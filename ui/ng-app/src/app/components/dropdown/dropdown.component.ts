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

import { Component, OnInit, OnChanges, Input, Output, EventEmitter, ViewEncapsulation, ViewChild, ElementRef, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DropdownSearchMenu } from 'admiral-ui-common';
import * as I18n from 'i18next';

const NA = 'N/A';

DropdownSearchMenu.setI18n({
  t: (key, context) => {
    context = context || {};
    context.ns = 'base';

    return I18n.t(key, {
      ns: 'base',
      context: context
    })
  }
});

DropdownSearchMenu.configureCustomHooks();

@Component({
  selector: 'dropdown',
  template: '<div #holder class="dropdown-holder"></div>',
  styleUrls: ['./dropdown.component.scss'],
  encapsulation: ViewEncapsulation.None,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DropdownComponent),
      multi: true
    }
  ]
})
export class DropdownComponent implements OnInit, ControlValueAccessor {

  @ViewChild('holder')
  elHolder: ElementRef;

  @Input()
  title;
  @Input()
  searchPlaceholder;
  @Input()
  ddClass;

  private _options;
  private _value;
  private _disabled;

  private credentialInput: DropdownSearchMenu;
  private propagateChange = (_: any) => {};

  constructor() { }

  public ngOnInit() {
    var el = this.elHolder.nativeElement;
    this.credentialInput = new DropdownSearchMenu(el, {
      title: this.title,
      searchPlaceholder: this.searchPlaceholder
    });

    if (this.ddClass) {
      el.querySelector('.dropdown').classList.add(this.ddClass);
    }

    this.credentialInput.setClearOptionSelectCallback(() => {
      this.propagateChange(undefined);
    });

    this.credentialInput.setOptionSelectCallback(() => {
      this.propagateChange(this.credentialInput.getSelectedOption());
    });

    if (this._options) {
      this.credentialInput.setOptions(this._options);
    }
    if (this._value) {
      this.credentialInput.setSelectedOption(this._value);
    }
    if (this._disabled) {
      this.credentialInput.setDisabled(this._disabled);
    }
  }

  @Input()
  public set options(options: Array<any>) {
    this._options = options;
    if (this.credentialInput) {
      this.credentialInput.setOptions(options);
    }
  }

  writeValue(value: any) {
    if (typeof value === 'string') { // assume it's documentSelfLink
      if (!this._options) {
        return;
      }
      var option = this._options.filter(option => {
        return value === option.documentSelfLink;
      })[0];
      if (this.credentialInput && option) {
        this._value = option;
        this.credentialInput.setSelectedOption(option);
      }
    } else {
      this._value = value;
      if (this.credentialInput) {
        this.credentialInput.setSelectedOption(value);
      }
    }
  }
  registerOnChange(fn: any){
    this.propagateChange = fn;
  }
  registerOnTouched(fn: any) {

  }
  setDisabledState(isDisabled: boolean) {
    this._disabled = isDisabled;
    if (this.credentialInput) {
      this.credentialInput.setDisabled(isDisabled);
    }
  }


}


