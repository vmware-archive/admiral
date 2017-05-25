import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IdentityManagementComponent } from './identity-management.component';

describe('IdentityManagementComponent', () => {
  let component: IdentityManagementComponent;
  let fixture: ComponentFixture<IdentityManagementComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IdentityManagementComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IdentityManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
