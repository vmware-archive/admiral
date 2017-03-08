interface JQueryStatic {
    (): any;
}

declare var $: JQueryStatic;

declare module "jquery" {
    export = $;
}

export class Search {
    constructor(properties: any, changeCallback: any);
    setQueryOptions(options: any);
    getQueryOptions(): any;
    getEl(): Element;
}