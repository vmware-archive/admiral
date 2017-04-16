import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NavigationSlideTransitionComponent } from './navigation-slide-transition.component';

describe('NavigationSlideTransitionComponent', () => {
  let component: NavigationSlideTransitionComponent;
  let fixture: ComponentFixture<NavigationSlideTransitionComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NavigationSlideTransitionComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NavigationSlideTransitionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
