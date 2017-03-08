interface OCCURRENCE {
    PARAM: string;
    ALL: string;
    ANY: string;
    /* Not supported by DCP
    NONE: 'none'
    */
}


interface SC {
    SEARCH_CATEGORY_PARAM: string;
    SEARCH_OCCURRENCE: OCCURRENCE;
}

export var searchConstants: SC;