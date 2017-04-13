import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PublicRepositoriesComponent } from './public-repositories.component';

describe('PublicRepositoriesComponent', () => {
  let component: PublicRepositoriesComponent;
  let fixture: ComponentFixture<PublicRepositoriesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PublicRepositoriesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PublicRepositoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
