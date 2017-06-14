interface JQueryStatic {
  (): any;
}

declare var $: JQueryStatic;

declare module "jquery" {
  export = $;
}

export class SimpleSearch {

  constructor(displayPropertyName: any, sourceCallback: any, selectionCallback: any);

  getEl(): Element;

  getValue(): any;
  setValue(any);
}
