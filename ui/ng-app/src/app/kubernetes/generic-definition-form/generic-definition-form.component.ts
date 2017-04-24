import { Component, OnInit, ViewChild, Input } from '@angular/core';

@Component({
  selector: 'kubernetes-generic-definition-form',
  templateUrl: './generic-definition-form.component.html',
  styleUrls: ['./generic-definition-form.component.scss']
})
export class GenericDefinitionFormComponent implements OnInit {

  @Input() entity: any;
  private templateContent: string;
  private isImportingTemplate: boolean;

  @ViewChild('fileInput') fileInput;

  constructor() { }

  ngOnInit() {
  }

  browseFile() {
    this.fileInput.nativeElement.click();
  }
  onFileChange($event) {
    var files = $event.target.files;
    if (!files.length) {
      return;
    }
    this.loadFromFile(files[0]);
  }
  loadFromFile(file) {
    //TODO: file size validation

    var reader = new FileReader();

    reader.onload = (e) => {
      var target: any = e.target;
      this.templateContent = target.result;
    };
    reader.readAsText(file);
  }
  importTemplate() {
  }

}
