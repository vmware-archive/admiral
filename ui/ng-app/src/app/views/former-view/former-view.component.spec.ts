import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FormerViewComponent } from './former-view.component';

describe('FormerViewComponent', () => {
  let component: FormerViewComponent;
  let fixture: ComponentFixture<FormerViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FormerViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FormerViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
