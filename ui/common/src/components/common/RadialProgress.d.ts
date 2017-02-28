declare module "d3" {
  export function select(arg: any): any;
  export function interpolate(arg1: any, arg2: any): any;
  export function interpolateNumber(arg1: any, arg2: any): any;
  export var svg: any;
}

export class RadialProgress {
    constructor(m: Element);
    diameter(d: number): RadialProgress;
    label(l: string): RadialProgress;
    majorTitle(l: string): RadialProgress;
    minorTitle(l: string): RadialProgress;
    value(v: number): RadialProgress;
    render(): RadialProgress;
}