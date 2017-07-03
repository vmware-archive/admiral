import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MainResourcesComputeComponent } from './main-resources-compute.component';

describe('MainResourcesComponent', () => {
  let component: MainResourcesComputeComponent;
  let fixture: ComponentFixture<MainResourcesComputeComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ MainResourcesComputeComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MainResourcesComputeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
