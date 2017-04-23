import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { VolumeBoxComponent } from './volume-box.component';

describe('VolumeBoxComponent', () => {
  let component: VolumeBoxComponent;
  let fixture: ComponentFixture<VolumeBoxComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ VolumeBoxComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(VolumeBoxComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
