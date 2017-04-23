import { Component, OnInit, ViewChild } from '@angular/core';
import { TemplateService } from '../../../utils/template.service';
import { Utils } from '../../../utils/utils';
import { Router, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-template-importer',
  templateUrl: './template-importer.component.html',
  styleUrls: ['./template-importer.component.scss']
})
export class TemplateImporterComponent implements OnInit {

  private templateContent: string;
  private isImportingTemplate: boolean;

  @ViewChild('fileInput') fileInput;

  constructor(private templateService: TemplateService, private router: Router) { }

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
    if (this.templateContent) {
      this.isImportingTemplate = true;
      this.templateService.importContainerTemplate(this.templateContent).then((templateSelfLink) => {

        var documentId = Utils.getDocumentId(templateSelfLink);

        this.router.navigate(['home/templates/' + documentId]);
      });
    }
  }

}
