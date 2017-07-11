interface FT {
    formatBytes(bytes): string;
    toBytes(value, unit): number;
    fromBytes(bytes): any;
    calculateMemorySize(bytes): any;
    escapeHtml(htmlString: string): string
}

export var formatUtils: FT;