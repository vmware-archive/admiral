import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { TagDetailsContainersComponent } from './tag-details-containers.component';

describe('TagDetailsContainersComponent', () => {
  let component: TagDetailsContainersComponent;
  let fixture: ComponentFixture<TagDetailsContainersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ TagDetailsContainersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TagDetailsContainersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
