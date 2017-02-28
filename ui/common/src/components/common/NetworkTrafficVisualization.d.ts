declare module "d3" {
  export function select(arg: any): any;
  export function interpolate(arg1: any, arg2: any): any;
  export function interpolateNumber(arg1: any, arg2: any): any;
  export var svg: any;
}

export class NetworkTrafficVisualization {
    constructor(m: Element, a: any);
    setData(receivedBytes, sentBytes);
    reset(label);
}