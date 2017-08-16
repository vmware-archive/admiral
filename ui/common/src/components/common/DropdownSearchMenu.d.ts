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

interface JQueryStatic {
  (): any;
}

declare var $: JQueryStatic;

declare module "jquery" {
  export = $;
}

export class DropdownSearchMenu {

  constructor($el: any, componentOptions: any);

  setFilterCallback(callback);
  setOptions(options);
  setManageOptions(manageOptions);
  setFilter(filter);
  setLoading(isLoading);
  setDisabled(disabled);
  setSelectedOption(option);
  getSelectedOption();
  setOptionSelectCallback(optionSelectCallback);
  setManageOptionSelectCallback(manageOptionSelectCallback);
  setClearOptionSelectCallback(clearOptionSelectCallback);
  setOptionsRenderer(optionsRenderer);
  setValueRenderer(valueRenderer);

  static setI18n(i18n);
  static configureCustomHooks();
}
