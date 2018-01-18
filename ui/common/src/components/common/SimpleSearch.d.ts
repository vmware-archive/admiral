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

interface JQueryStatic {
  (): any;
}

declare var $: JQueryStatic;

declare module "jquery" {
  export = $;
}

/**
 * Component providing simple search typeahead functionality.
 */
export class SimpleSearch {

  constructor(displayPropertyName: any, sourceCallback: any, selectionCallback: any);

  getEl(): Element;

  getValue(): any;
  setValue(any);
}
