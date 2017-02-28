interface FT {
    formatBytes(bytes): string;
    toBytes(value, unit): number;
    fromBytes(bytes): any;
    calculateMemorySize(bytes): any;
}

export var formatUtils: FT;