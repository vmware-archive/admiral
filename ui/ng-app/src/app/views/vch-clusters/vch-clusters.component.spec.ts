import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { VchClustersComponent } from './vch-clusters.component';

describe('VchClustersComponent', () => {
  let component: VchClustersComponent;
  let fixture: ComponentFixture<VchClustersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ VchClustersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(VchClustersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
