import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { TemplateImporterComponent } from './template-importer.component';

describe('TemplateImporterComponent', () => {
  let component: TemplateImporterComponent;
  let fixture: ComponentFixture<TemplateImporterComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ TemplateImporterComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TemplateImporterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
