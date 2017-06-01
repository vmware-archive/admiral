import { Injectable, EventEmitter } from '@angular/core';

@Injectable()
export class ViewExpandRequestService {
  private fullScreenRequestEmitter: EventEmitter<boolean> = new EventEmitter();
  private expandRequestEmitter: EventEmitter<boolean> = new EventEmitter();

  constructor() {}

  requestFullScreen(value) {
    this.fullScreenRequestEmitter.emit(value);
  }

  getFullScreenRequestEmitter(): EventEmitter<boolean> {
    return this.fullScreenRequestEmitter;
  }
}
