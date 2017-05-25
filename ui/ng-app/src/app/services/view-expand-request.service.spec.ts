import { TestBed, inject } from '@angular/core/testing';

import { ViewExpandRequestService } from './view-expand-request.service';

describe('ViewExpandRequestService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ViewExpandRequestService]
    });
  });

  it('should ...', inject([ViewExpandRequestService], (service: ViewExpandRequestService) => {
    expect(service).toBeTruthy();
  }));
});
