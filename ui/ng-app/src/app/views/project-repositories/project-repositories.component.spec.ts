import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProjectRepositoriesComponent } from './project-repositories.component';

describe('ProjectRepositoriesComponent', () => {
  let component: ProjectRepositoriesComponent;
  let fixture: ComponentFixture<ProjectRepositoriesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProjectRepositoriesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProjectRepositoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
