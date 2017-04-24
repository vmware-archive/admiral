import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { GenericTemplateItemComponent } from './generic-template-item.component';

describe('GenericTemplateItemComponent', () => {
  let component: GenericTemplateItemComponent;
  let fixture: ComponentFixture<GenericTemplateItemComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ GenericTemplateItemComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(GenericTemplateItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
