import { Injectable, EventEmitter } from '@angular/core';

@Injectable()
export class ViewExpandRequestService {
  private requestEmitter: EventEmitter<boolean> = new EventEmitter();

  constructor() {}

  request(isExpand) {
    this.requestEmitter.emit(isExpand);
  }

  getRequestEmitter(): EventEmitter<boolean> {
    return this.requestEmitter;
  }

}
