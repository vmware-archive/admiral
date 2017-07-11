/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */
module.exports = function (componentOptions) {
    var searchHtml = '';
    if (!componentOptions.searchDisabled) {
        searchHtml = "<div class=\"dropdown-search\">\n          <div class=\"search-input\">\n            <input type=\"text\" placeholder=\"" + componentOptions.searchPlaceholder + "\">\n            <i class=\"fa fa-spinner fa-spin loader-inline form-control-feedback\"></i>\n            <i class=\"fa fa-search search-hint form-control-feedback\"></i>\n          </div>\n        </div>";
    }
    return "\n    <div class=\"dropdown dropdown-select\">\n      <button class=\"dropdown-toggle\" data-toggle=\"dropdown\" aria-expanded=\"true\">\n        <div class=\"dropdown-title placeholder\">" + componentOptions.title + "</div>\n        <div class=\"loading hide\">\n          <div class=\"spinner spinner-inline\"></div>\n        </div>\n      </button>\n      <div class=\"dropdown-menu\">\n        " + searchHtml + "\n        <ul class=\"dropdown-options\" role=\"menu\" aria-labelledby=\"menu1\">\n        <!-- Runtime options will appear here -->\n        </ul>\n        <div class=\"divider hide\" role=\"presentation\"></div>\n        <ul class=\"dropdown-manage hide\">\n        <!-- Runtime menu options will appear here -->\n        </ul>\n      </div>\n    </div>";
};
//# sourceMappingURL=DropdownSearchMenuTemplate.js.map