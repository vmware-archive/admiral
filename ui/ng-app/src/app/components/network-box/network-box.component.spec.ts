import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NetworkBoxComponent } from './network-box.component';

describe('NetworkBoxComponent', () => {
  let component: NetworkBoxComponent;
  let fixture: ComponentFixture<NetworkBoxComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NetworkBoxComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NetworkBoxComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
