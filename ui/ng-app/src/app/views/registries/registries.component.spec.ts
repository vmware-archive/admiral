import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RegistriesComponent } from './registries.component';

describe('RegistriesComponent', () => {
  let component: RegistriesComponent;
  let fixture: ComponentFixture<RegistriesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RegistriesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RegistriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
