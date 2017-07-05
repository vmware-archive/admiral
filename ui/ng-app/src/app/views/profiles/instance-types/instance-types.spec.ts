import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InstanceTypesComponent } from './instance-types.component';

describe('InstanceTypesComponent', () => {
  let component: InstanceTypesComponent;
  let fixture: ComponentFixture<InstanceTypesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InstanceTypesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InstanceTypesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
