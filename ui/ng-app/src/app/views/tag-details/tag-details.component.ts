import { Route, ActivatedRoute, Router } from '@angular/router';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-tag-details',
  templateUrl: './tag-details.component.html',
  styleUrls: ['./tag-details.component.scss']
})
export class TagDetailsComponent implements OnInit {

  tagId: string;
  repositoryId: string;

  constructor(private route: ActivatedRoute, private router: Router) { }

  ngOnInit() {
    this.repositoryId = this.route.snapshot.params['rid'];
    this.tagId = this.route.snapshot.params['tid'];
  }

  goBack(tag: string) {
    this.router.navigate(['../../../../'], {relativeTo: this.route});
  }

}
