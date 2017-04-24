import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { GenericDefinitionFormComponent } from './generic-definition-form.component';

describe('GenericDefinitionFormComponent', () => {
  let component: GenericDefinitionFormComponent;
  let fixture: ComponentFixture<GenericDefinitionFormComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ GenericDefinitionFormComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(GenericDefinitionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
