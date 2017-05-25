import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MainResourcesComponent } from './main-resources.component';

describe('MainResourcesComponent', () => {
  let component: MainResourcesComponent;
  let fixture: ComponentFixture<MainResourcesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ MainResourcesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MainResourcesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
