interface SU {
    buildOdataQuery(queryOptions: any): string;
    calculateLimit(): number;
    encodeQuotes(value: string): string;
}

export var serviceUtils: SU;