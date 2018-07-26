webpackJsonp([1,4],{

/***/ 144:
/***/ (function(module, exports) {

function webpackEmptyContext(req) {
	throw new Error("Cannot find module '" + req + "'.");
}
webpackEmptyContext.keys = function() { return []; };
webpackEmptyContext.resolve = webpackEmptyContext;
module.exports = webpackEmptyContext;
webpackEmptyContext.id = 144;


/***/ }),

/***/ 145:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
__webpack_require__(164);
var platform_browser_dynamic_1 = __webpack_require__(159);
var core_1 = __webpack_require__(9);
var environment_1 = __webpack_require__(163);
var _1 = __webpack_require__(162);
if (environment_1.environment.production) {
    core_1.enableProdMode();
}
platform_browser_dynamic_1.platformBrowserDynamic().bootstrapModule(_1.AppModule);
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/main.js.map

/***/ }),

/***/ 160:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var platform_browser_1 = __webpack_require__(33);
var core_1 = __webpack_require__(9);
var forms_1 = __webpack_require__(89);
var http_1 = __webpack_require__(90);
var clarity_angular_1 = __webpack_require__(93);
var app_component_1 = __webpack_require__(91);
var utils_module_1 = __webpack_require__(167);
var app_routing_1 = __webpack_require__(161);
var contributors_service_1 = __webpack_require__(92);
var AppModule = (function () {
    function AppModule() {
    }
    return AppModule;
}());
AppModule = __decorate([
    core_1.NgModule({
        declarations: [
            app_component_1.AppComponent
        ],
        imports: [
            platform_browser_1.BrowserModule,
            forms_1.FormsModule,
            http_1.HttpModule,
            clarity_angular_1.ClarityModule.forRoot(),
            utils_module_1.UtilsModule,
            app_routing_1.ROUTING
        ],
        providers: [contributors_service_1.ContributorService],
        bootstrap: [app_component_1.AppComponent]
    })
], AppModule);
exports.AppModule = AppModule;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/app/app.module.js.map

/***/ }),

/***/ 161:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
var router_1 = __webpack_require__(60);
exports.ROUTES = [
    { path: '', redirectTo: 'home', pathMatch: 'full' }
];
exports.ROUTING = router_1.RouterModule.forRoot(exports.ROUTES);
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/app/app.routing.js.map

/***/ }),

/***/ 162:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
Object.defineProperty(exports, "__esModule", { value: true });
__export(__webpack_require__(91));
__export(__webpack_require__(160));
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/app/index.js.map

/***/ }),

/***/ 163:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
// The file contents for the current environment will overwrite these during build.
// The build system defaults to the dev environment which uses `environment.ts`, but if you do
// `ng build --env=prod` then `environment.prod.ts` will be used instead.
// The list of which env maps to which file can be found in `angular-cli.json`.

Object.defineProperty(exports, "__esModule", { value: true });
exports.environment = {
    production: true
};
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/environments/environment.js.map

/***/ }),

/***/ 164:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

Object.defineProperty(exports, "__esModule", { value: true });
// This file includes polyfills needed by Angular 2 and is loaded before
// the app. You can add your own extra polyfills to this file.
__webpack_require__(181);
__webpack_require__(174);
__webpack_require__(170);
__webpack_require__(176);
__webpack_require__(175);
__webpack_require__(173);
__webpack_require__(172);
__webpack_require__(180);
__webpack_require__(169);
__webpack_require__(168);
__webpack_require__(178);
__webpack_require__(171);
__webpack_require__(179);
__webpack_require__(177);
__webpack_require__(182);
__webpack_require__(361);
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/polyfills.js.map

/***/ }),

/***/ 165:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/*
 * Hack while waiting for https://github.com/angular/angular/issues/6595 to be fixed.
 */

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(9);
var router_1 = __webpack_require__(60);
var HashListener = (function () {
    function HashListener(route) {
        var _this = this;
        this.route = route;
        this.sub = this.route.fragment.subscribe(function (f) {
            _this.scrollToAnchor(f, false);
        });
    }
    HashListener.prototype.ngOnInit = function () {
        this.scrollToAnchor(this.route.snapshot.fragment, false);
    };
    HashListener.prototype.scrollToAnchor = function (hash, smooth) {
        if (smooth === void 0) { smooth = true; }
        if (hash) {
            var element = document.querySelector("#" + hash);
            if (element) {
                element.scrollIntoView({
                    behavior: smooth ? "smooth" : "instant",
                    block: "start"
                });
            }
        }
    };
    HashListener.prototype.ngOnDestroy = function () {
        this.sub.unsubscribe();
    };
    return HashListener;
}());
HashListener = __decorate([
    core_1.Directive({
        selector: "[hash-listener]",
        host: {
            "[style.position]": "'relative'"
        }
    }),
    __metadata("design:paramtypes", [typeof (_a = typeof router_1.ActivatedRoute !== "undefined" && router_1.ActivatedRoute) === "function" && _a || Object])
], HashListener);
exports.HashListener = HashListener;
var _a;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/utils/hash-listener.directive.js.map

/***/ }),

/***/ 166:
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/*
 * Hack while waiting for https://github.com/angular/angular/issues/6595 to be fixed.
 */

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(9);
var router_1 = __webpack_require__(60);
var ScrollSpy = (function () {
    function ScrollSpy(renderer) {
        this.renderer = renderer;
        this.anchors = [];
        this.throttle = false;
    }
    Object.defineProperty(ScrollSpy.prototype, "links", {
        set: function (routerLinks) {
            var _this = this;
            this.anchors = routerLinks.map(function (routerLink) { return "#" + routerLink.fragment; });
            this.sub = routerLinks.changes.subscribe(function () {
                _this.anchors = routerLinks.map(function (routerLink) { return "#" + routerLink.fragment; });
            });
        },
        enumerable: true,
        configurable: true
    });
    ScrollSpy.prototype.handleEvent = function () {
        var _this = this;
        this.scrollPosition = this.scrollable.scrollTop;
        if (!this.throttle) {
            window.requestAnimationFrame(function () {
                var currentLinkIndex = _this.findCurrentAnchor() || 0;
                _this.linkElements.forEach(function (link, index) {
                    _this.renderer.setElementClass(link.nativeElement, "active", index === currentLinkIndex);
                });
                _this.throttle = false;
            });
        }
        this.throttle = true;
    };
    ScrollSpy.prototype.findCurrentAnchor = function () {
        for (var i = this.anchors.length - 1; i >= 0; i--) {
            var anchor = this.anchors[i];
            if (this.scrollable.querySelector(anchor) && this.scrollable.querySelector(anchor).offsetTop <= this.scrollPosition) {
                return i;
            }
        }
    };
    ScrollSpy.prototype.ngOnInit = function () {
        this.scrollable.addEventListener("scroll", this);
    };
    ScrollSpy.prototype.ngOnDestroy = function () {
        this.scrollable.removeEventListener("scroll", this);
        if (this.sub) {
            this.sub.unsubscribe();
        }
    };
    return ScrollSpy;
}());
__decorate([
    core_1.Input("scrollspy"),
    __metadata("design:type", Object)
], ScrollSpy.prototype, "scrollable", void 0);
__decorate([
    core_1.ContentChildren(router_1.RouterLinkWithHref, { descendants: true }),
    __metadata("design:type", typeof (_a = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _a || Object),
    __metadata("design:paramtypes", [typeof (_b = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _b || Object])
], ScrollSpy.prototype, "links", null);
__decorate([
    core_1.ContentChildren(router_1.RouterLinkWithHref, { descendants: true, read: core_1.ElementRef }),
    __metadata("design:type", typeof (_c = typeof core_1.QueryList !== "undefined" && core_1.QueryList) === "function" && _c || Object)
], ScrollSpy.prototype, "linkElements", void 0);
ScrollSpy = __decorate([
    core_1.Directive({
        selector: "[scrollspy]",
    }),
    __metadata("design:paramtypes", [typeof (_d = typeof core_1.Renderer !== "undefined" && core_1.Renderer) === "function" && _d || Object])
], ScrollSpy);
exports.ScrollSpy = ScrollSpy;
var _a, _b, _c, _d;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/utils/scrollspy.directive.js.map

/***/ }),

/***/ 167:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(9);
var hash_listener_directive_1 = __webpack_require__(165);
var scrollspy_directive_1 = __webpack_require__(166);
var clarity_angular_1 = __webpack_require__(93);
var common_1 = __webpack_require__(40);
var UtilsModule = (function () {
    function UtilsModule() {
    }
    return UtilsModule;
}());
UtilsModule = __decorate([
    core_1.NgModule({
        imports: [
            common_1.CommonModule,
            clarity_angular_1.ClarityModule.forChild()
        ],
        declarations: [
            hash_listener_directive_1.HashListener,
            scrollspy_directive_1.ScrollSpy
        ],
        exports: [
            hash_listener_directive_1.HashListener,
            scrollspy_directive_1.ScrollSpy
        ]
    })
], UtilsModule);
exports.UtilsModule = UtilsModule;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/utils/utils.module.js.map

/***/ }),

/***/ 322:
/***/ (function(module, exports, __webpack_require__) {

exports = module.exports = __webpack_require__(38)(false);
// imports


// module
exports.push([module.i, ".clr-icon.vmware-logo {\n  background: url(/admiral/images/vmware.svg) no-repeat left 9px;\n  width: 108px; }\n\n.hero {\n  background-color: #ddd;\n  left: -24px;\n  padding-bottom: 2em;\n  padding-top: 2em;\n  overflow-x: hidden;\n  position: relative;\n  text-align: center;\n  top: -24px; }\n  .hero .btn-custom {\n    display: inline-block;\n    text-align: center;\n    margin: auto; }\n\n.content-area {\n  overflow-x: hidden; }\n\n.hero-image img {\n  max-width: 360px; }\n\n.icon {\n  display: inline-block;\n  height: 32px;\n  vertical-align: middle;\n  width: 32px; }\n  .icon.icon-github {\n    background: url(/admiral/images/github_icon.svg) no-repeat left -2px; }\n\n.nav-group label {\n  display: block;\n  margin-bottom: 1em; }\n\n.sidenav .nav-link {\n  padding: 3px 6px; }\n  .sidenav .nav-link:hover {\n    background: #eee; }\n  .sidenav .nav-link.active {\n    background: #d9e4ea;\n    color: #000; }\n\n.section {\n  padding: .5em 0; }\n\n.contributor {\n  border-radius: 50%;\n  border: 1px solid #ccc;\n  margin-bottom: 1.5em;\n  margin-right: 1em;\n  max-width: 64px;\n  text-decoration: none; }\n\n@media (min-width: 320px) {\n  .title {\n    display: none; }\n  .hero {\n    width: 100vw; }\n  #license {\n    padding-bottom: 20vh; } }\n\n@media (min-width: 768px) {\n  .title {\n    display: block; }\n  .hero {\n    width: 110%; }\n  #license {\n    padding-bottom: 78vh; } }\n\n.row:after {\n  clear: both;\n  content: \"\";\n  display: table; }\n", ""]);

// exports


/*** EXPORTS FROM exports-loader ***/
module.exports = module.exports.toString();

/***/ }),

/***/ 331:
/***/ (function(module, exports) {

module.exports = "<clr-main-container>\n    <header class=\"header header-6\">\n        <div class=\"branding\">\n            <a href=\"https://vmware.github.io/\" class=\"nav-link\">\n                <span class=\"clr-icon vmware-logo\"></span>\n                <span class=\"title\">Open Source Program Office</span>\n            </a>\n        </div>\n    </header>\n    <div class=\"content-container\">\n        <div id=\"content-area\" class=\"content-area\" hash-listener #scrollable>\n            <div class=\"hero\">\n                <div class=\"hero-image\"><img src=\"images/admiral.png\" alt=\"\"></div>\n                <h3>Highly Scalable Container Management Platform</h3>\n                <p><a href=\"https://github.com/vmware/admiral\" class=\"btn btn-primary\"><i class=\"icon icon-github\"></i> Fork Admiral&trade;</a></p>\n            </div>\n            <div id=\"overview\" class=\"section\">\n                <h2>What is Admiral&trade;</h2>\n\n                <p>Admiral&trade; is a highly scalable and very lightweight Container Management platform for deploying and managing container based applications. It is designed to have a small footprint and boot extremely quickly. Admiral&trade; is intended to provide automated deployment and lifecycle management of containers.</p>\n\n                <br>\n\n                <ul>\n                    <li><strong>Rule-based resource management</strong> - Setup your deployment preferences to let Admiral&trade; manage container placement.</li>\n                    <li><strong>Live state updates</strong> - Provides a live view of your system.</li>\n                    <li><strong>Efficient multi-container template management</strong> - Enables logical multi-container application deployments.</li>\n                </ul>\n            </div>\n\n            <div id=\"gettingAdmiral\" class=\"section\">\n                <h2>Getting Admiral&trade;</h2>\n\n                <p>Open source license information may be found in Admiral&trade; <a href=\"https://github.com/vmware/admiral/blob/master/LICENSE\">Open Source License</a> file.</p>\n                <p>Admiral&trade; source code is available on the VMware <a href=\"https://github.com/vmware/admiral\">Admiral&trade; GitHub source repository</a>. You can build your own Admiral&trade; jar by cloning the repo and following the instructions in <a href=\"https://github.com/vmware/admiral/blob/master/README.md\">README.md</a>.</p>\n                <p>Admiral&trade; container image is available on <a href=\"https://hub.docker.com/r/vmware/admiral/\">Docker Hub</a>.</p>\n                \n                <p>To get the latest stable release run:</p>\n                <pre><code clr-code-highlight=\"language-bash\">docker run -d -p 8282:8282 --name admiral vmware/admiral</code></pre>\n                \n                <p>To get the latest development release with all beta features run:</p>\n                <pre><code clr-code-highlight=\"language-bash\">docker run -d -p 8282:8282 --name admiral vmware/admiral:dev</code></pre>\n                \n                <p>Admiral can also be started as a standalone Java\n                application. Download the latest development release\n                <a href=\"https://bintray.com/vmware/admiral/download_file?file_path=com%2Fvmware%2Fadmiral%2Fadmiral-host%2F1.4.2-SNAPSHOT%2Fadmiral-host-1.4.2-20180723.130833-1-uber-jar-with-agent.jar\">admiral-host-1.4.2-20180723.130833-1-uber-jar-with-agent.jar</a> and\n                run it with:</p>\n                <pre><code clr-code-highlight=\"language-bash\">java -jar admiral-host-1.4.2-20180723.130833-1-uber-jar-with-agent.jar --bindAddress=127.0.0.1 --port=8282</code></pre>\n                \n                <p>To access the UI when deployed on docker or as a standalone process, point your browser respectively to:</p>\n                \n                <pre><code clr-code-highlight=\"language-bash\">http://&lt;docker_host_ip&gt;:8282 or http://localhost:8282</code></pre>\n                \n                <p>Note that authentication is disabled by default and can be enabled through the configuration.</p>\n            </div>\n\n            <div id=\"gettingStarted\" class=\"section\">\n                <h2>Getting Started</h2>\n                <p>We've provided a guide to help get you started:</p>\n\n                <a href=\"https://github.com/vmware/admiral/wiki/User-Guide\" class=\"btn btn-outline\">User guide for Admiral™</a>\n                <a href=\"https://github.com/vmware/admiral/wiki/Developer-Guide\" class=\"btn btn-outline\">Developer guide for Admiral™</a>\n            </div>\n\n            <div id=\"support\" class=\"section\">\n                <h2>Support</h2>\n                <p>Admiral&trade; is released as open source software and, presently, provides community support through our GitHub project page. If you encounter an issue or have a question, feel free to reach out via <a href=\"https://github.com/vmware/admiral/issues\">GitHub issues for Admiral&trade;</a>.</p>\n            </div>\n            <div id=\"contributors\" class=\"section\">\n                <h2>Contributors</h2>\n\n                <br>\n\n                <ul class=\"list-unstyled row\">\n                    <li *ngFor=\"let contributor of contributors\"><a [href]=\"contributor.html_url\"><img [src]=\"contributor.avatar_url\" alt=\"\" class=\"contributor\"></a></li>\n                </ul>\n            </div>\n\n            <div id=\"contributing\" class=\"section\">\n                <h2>Contributing</h2>\n\n                <p>You are invited to contribute new features, fixes, or updates, large or small; we are always thrilled to receive <a href=\"https://help.github.com/articles/creating-a-pull-request\">pull requests</a>, and do our best to process them as fast as we can. If you wish to contribute code, you should sign <a href=\"https://vmware.github.io/admiral/files/vmware_cla.pdf\">Contributor License Agreement</a> and return a copy to <a href=\"mailto:osscontributions@vmware.com\">osscontributions@vmware.com</a> before we can merge your contribution. For any questions about the CLA process, please refer to our <a href=\"https://cla.vmware.com/faq\">FAQ</a>.</p>\n\n                <p>Before you start to code, we recommend discussing your plans through a <a href=\"https://github.com/vmware/admiral/issues\">GitHub issue</a> or discuss it first with the official project maintainers via the <a href=\"https://vmware.slack.com/messages/C27L2FUN4\">Slack chat</a>, especially for more ambitious contributions. This gives other contributors a chance to point you in the right direction, give you feedback on your design, and help you find out if someone else is working on the same thing.</p>\n            </div>\n\n            <div id=\"license\" class=\"section\">\n                <h2>License</h2>\n\n                <p>Admiral&trade; is licensed under Apache License Version 2.0 as documented in the <a href=\"https://github.com/vmware/admiral/blob/master/LICENSE\">open source license file</a> accompanying the Admiral&trade; distribution.</p>\n            </div>\n        </div>\n        <nav class=\"sidenav\" [clr-nav-level]=\"2\">\n            <section class=\"sidenav-content\">\n                <section class=\"nav-group\" [scrollspy]=\"scrollable\">\n                    <label><a class=\"nav-link active\" routerLink=\".\" routerLinkActive=\"active\" fragment=\"overview\">Overview</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"gettingAdmiral\">Getting Admiral&trade;</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"gettingStarted\">Getting Started</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"support\">Support</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"contributors\">Contributors</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"contributing\">Contributing</a></label>\n                    <label class=\"bump-down\"><a class=\"nav-link\" routerLink=\".\" fragment=\"license\">License</a></label>\n                </section>\n            </section>\n        </nav>\n    </div>\n</clr-main-container>\n"

/***/ }),

/***/ 363:
/***/ (function(module, exports, __webpack_require__) {

module.exports = __webpack_require__(145);


/***/ }),

/***/ 91:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(9);
var contributors_service_1 = __webpack_require__(92);
var AppComponent = (function () {
    function AppComponent(contributorSvc) {
        this.contributorSvc = contributorSvc;
        this.contributors = [];
    }
    AppComponent.prototype.ngOnInit = function () {
        var _this = this;
        this.contributorSvc.getContributors().subscribe(function (results) {
            _this.contributors = results;
            // console.log("Contribs: ", results);
        });
    };
    return AppComponent;
}());
AppComponent = __decorate([
    core_1.Component({
        selector: 'my-app',
        template: __webpack_require__(331),
        styles: [__webpack_require__(322)]
    }),
    __metadata("design:paramtypes", [typeof (_a = typeof contributors_service_1.ContributorService !== "undefined" && contributors_service_1.ContributorService) === "function" && _a || Object])
], AppComponent);
exports.AppComponent = AppComponent;
var _a;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/app/app.component.js.map

/***/ }),

/***/ 92:
/***/ (function(module, exports, __webpack_require__) {

"use strict";

var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
var core_1 = __webpack_require__(9);
var http_1 = __webpack_require__(90);
var Observable_1 = __webpack_require__(7);
__webpack_require__(336);
__webpack_require__(337);
var ContributorService = (function () {
    function ContributorService(http) {
        this.http = http;
    }
    ContributorService.prototype.getContributors = function () {
        // do work to merge three http calls into one observable.
        return Observable_1.Observable.forkJoin([
            this.http.get('https://api.github.com/repos/vmware/admiral/contributors')
                .map(function (res) { return res.json(); })
        ])
            .map(function (data) {
            var contributors = [];
            // console.logco("observable data", data); // make sure we are getting datas from github.
            // concat all the data into one array
            contributors = contributors.concat(data[0]);
            // create a uniqueContributors array
            var uniqueContributors = [];
            // filteredContributors filters contributors array, add it to uniqueContributors if its not already there.
            var filteredContributors = contributors.filter(function (el) {
                if (uniqueContributors.indexOf(el.id) === -1) {
                    uniqueContributors.push(el.id);
                    return true;
                }
                else {
                    return false;
                }
            });
            contributors = filteredContributors;
            return contributors;
        });
    };
    return ContributorService;
}());
ContributorService = __decorate([
    core_1.Injectable(),
    __metadata("design:paramtypes", [typeof (_a = typeof http_1.Http !== "undefined" && http_1.Http) === "function" && _a || Object])
], ContributorService);
exports.ContributorService = ContributorService;
var _a;
//# sourceMappingURL=/Users/mborisov/Documents/MyData/Repos/gh-page-template/admiral/src/src/src/services/contributors.service.js.map

/***/ })

},[363]);
//# sourceMappingURL=main.bundle.js.map