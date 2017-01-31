/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import ClosureDefinitionFormTemplate from 'components/closures/ClosureDefinitionFormTemplate.html';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import Component from 'components/common/Component';
import utils from 'core/utils';


let closureDefinitionConstraints = {
  name: function(name) {
    if (!name || validator.trim(name).length === 0) {
      return 'errors.required';
    }
  },
  runtime: function(runtime) {
    if (!runtime || validator.trim(runtime).length === 0) {
      return 'errors.required';
    }
  },
  entrypoint: function(entrypoint) {
    if (entrypoint && entrypoint.indexOf('.') <= 0) {
      return 'errors.entrypoint';
    }
  },
  inputs: function(inputs) {
    if (inputs) {
      try {
        JSON.parse(inputs);
      } catch (err) {
        return 'errors.inputs';
      }
    }
  },
  sourceConfig: function(sourceConfig) {
    if (!sourceConfig) {
      return null;
    }

    var error = {};

    var urlPathConfig = {
      require_tld: false,
      allow_underscores: true
    };

    if (sourceConfig.sourceMode === 'SourceURL') {
      if (!sourceConfig.sourceURL || validator.trim(sourceConfig.sourceURL).length === 0) {
        error.sourceURL = 'errors.required';
      } else if (!validator.isURL(sourceConfig.sourceURL, urlPathConfig)) {
        error.sourceURL = 'errors.urlPath';
      }
    } else if (sourceConfig.sourceMode === 'Source') {
      if (!sourceConfig.source || validator.trim(sourceConfig.source).length === 0) {
        error.source = 'errors.required';
      }
    }

    if (Object.keys(error).length > 0) {
      return error;
    }
    return null;
  },
  memoryLimit: function(megabytes) {
    if (megabytes) {
      if (!utils.isNonNegativeInteger(megabytes) || (megabytes < 50 || megabytes > 1536)) {
        return 'errors.nonNegativeNumberMemoryRange';
      }
    }
    return null;
  },
  timeout: function(timeout) {
    if (timeout && !utils.isNonNegativeInteger(timeout)) {
      return 'errors.nonNegativeNumber';
    }
    return null;
  }
};

class ClosureDefinitionForm extends Component {
  constructor() {
    super();

    this.$el = $(ClosureDefinitionFormTemplate());

    this.$el.find('.fa-question-circle').tooltip({
      html: true
    });

    this.logConfigOptionsEditor = new MulticolumnInputs(
      this.$el.find('.closure-logconfig-options-input .form-control'), {
        name: enhanceLabels('app.closure.request.inputs.logConfigOptionsInputs.name'),
        value: enhanceLabels('app.closure.request.inputs.logConfigOptionsInputs.value')
      }
    );

    this.closureInputsEditor = new MulticolumnInputs(
      this.$el.find('.closure-inputs .form-control'), {
        name: enhanceLabels('app.closure.request.inputs.closureInputs.name'),
        value: enhanceLabels('app.closure.request.inputs.closureInputs.value')
      }
    );

    this.closureOutputsEditor = new MulticolumnInputs(
      this.$el.find('.closure-outputs .form-control'), {
        name: enhanceLabels('app.closure.request.inputs.closureOutputs.name')
      }
    );

    this.$el.find('.closure-source-mode-input input').change(function() {
      sourceModeChanged(this.value);
    });

    this.$el.find('.closure-source-mode-input input[value=Source]').prop('checked', true);
    sourceModeChanged('Source');

    this.$el.find('.closure-source-url-input').hide();

    let $dependenciesEditor = this.$el.find('.closure-dependencies-input .dependencies-edit-area');
    this.dependenciesEditor = ace.edit($dependenciesEditor[0]);
    this.dependenciesEditor.getSession().setMode('ace/mode/json');
    this.dependenciesEditor.getSession().setTabSize(2);
    this.dependenciesEditor.getSession().setUseWrapMode(true);
    this.dependenciesEditor.setOptions({
      enableBasicAutocompletion: true
    });

    this.$el.find('.closure-dependencies-input').hide();
    this.$el.find('.closure-dependencies-check .checkbox-control').click(function() {
      $('.closure-dependencies-input').toggle(this.checked);
    });

    let $sourceEditor = this.$el.find('.closure-source-code-input .source-edit-area');
    this.sourceEditor = ace.edit($sourceEditor[0]);
    this.sourceEditor.getSession().setMode('ace/mode/javascript');
    this.sourceEditor.setOptions({
      enableBasicAutocompletion: true,
      enableSnippets: true,
      enableLiveAutocompletion: false
    });

    let srcEditor = this.sourceEditor;
    let depEditor = this.dependenciesEditor;
    this.$el.find('.closure-runtime-input .form-control').change(function() {
      updateCodeFormater(this.value, srcEditor);
      updateDependenciesFormater(this.value, depEditor);
    });

    let tabsToDeactivate = ['closure-code', 'closure-configuration'];
    let thisEl = $(this.$el);
    tabsToDeactivate.forEach(function(el) {
      thisEl.find('.container-form-content .nav a[href="#' + el + '"]')
          .on('click', function(e) {
            validateNameInput(e);
          });
    });
    this.$el.find('.closure-name-input .form-control')
          .focusout(function(e) {
            validateNameInput(e);
    });

    this.$el.find('.nav-item a[href="#closure-definition"]').tab('show');
    this.$el.find('#closure-definition.tab-pane').addClass('active');
  }

  setData(data) {
    if (this.data !== data) {
      updateForm.call(this, data);

      this.data = data;
    }
  }

  getEl() {
    return this.$el;
  }

  getRawInput() {
    let rawInput = {};

    rawInput.name = this.$el.find('.closure-name-input .form-control').val();
    rawInput.description = this.$el.find('.closure-description-input .form-control').val();
    rawInput.runtime = this.$el.find('.closure-runtime-input select').val();
    rawInput.entrypoint = this.$el.find('.closure-entrypoint-input .form-control').val();

    let sourceConfig = {};
    sourceConfig.sourceMode = this.$el.find(
      '.closure-source-mode-input input:radio:checked').val();
    if (sourceConfig.sourceMode === 'SourceURL') {
      sourceConfig.sourceURL = this.$el.find('.closure-source-url-input .form-control').val();
    } else {
      sourceConfig.source = this.sourceEditor.getValue();
    }

    rawInput.sourceConfig = sourceConfig;

    let rawInputData = this.closureInputsEditor.getData();
    let inputData = {};
    let inputNames = [];
    if (rawInputData) {
      rawInputData.forEach(function(e) {
        inputData[e.name] = JSON.parse(e.value);
        inputNames.push(e.name);
      });
    }
    rawInput.inputNames = inputNames;
    rawInput.inputs = JSON.stringify(inputData);

    let rawOutputData = this.closureOutputsEditor.getData();
    let outputNames = [];
    if (rawOutputData) {
      rawOutputData.forEach(function(e) {
        outputNames.push(e.name);
      });
    }
    rawInput.outputNames = outputNames;

    rawInput.dependencies = this.dependenciesEditor.getValue();

    let checkedDeps = this.$el.find('.closure-dependencies-check .checkbox-control').is(
      ':checked');
    if (!checkedDeps) {
      rawInput.dependencies = '';
    }

    rawInput.memoryLimit = this.$el.find('.closure-memory-limit-input .form-control').val();
    rawInput.timeout = this.$el.find('.closure-timeout-input .form-control').val();

    rawInput.logConfiguration = {};
    rawInput.logConfiguration.type = this.$el.find(
        '.closure-logconfig-driver-input .form-control')
      .val() || null;
    let rawData = this.logConfigOptionsEditor.getData();
    let configData = {};
    if (rawData) {
      rawData.forEach(function(e) {
        configData[e.name] = e.value;
      });
    }

    rawInput.logConfiguration.config = configData;

    return rawInput;
  }

  getClosureInputs() {
    let rawInput = this.getRawInput();
    if (rawInput.inputs) {
      return JSON.parse(rawInput.inputs);
    }

    return null;
  }

  /* convert a raw input object to DTO or read it from view */
  getClosureDefinition() {
    let input = this.getRawInput();
    let result = {};

    result.name = input.name;
    result.description = input.description;
    result.runtime = input.runtime;
    if (input.entrypoint) {
      result.entrypoint = input.entrypoint;
    }

    if (input.sourceConfig.sourceMode === 'SourceURL') {
      result.sourceURL = input.sourceConfig.sourceURL;
    } else {
      result.source = this.sourceEditor.getValue();
    }

    if (input.dependencies) {
      result.dependencies = input.dependencies;
    }
    if (input.outputNames) {
      result.outputNames = input.outputNames;
    }
    if (input.inputs) {
      result.inputs = JSON.parse(input.inputs);
    }
    if (input.memoryLimit) {
      result.resources = {};
      result.resources.ramMB = input.memoryLimit;
    }
    if (input.timeout) {
      if (typeof result.resources === 'undefined') {
        result.resources = {};
      }
      result.resources.timeoutSeconds = input.timeout;
    }

    if (input.logConfiguration) {
      result.logConfiguration = {};
      result.logConfiguration.type = input.logConfiguration.type;
      result.logConfiguration.config = input.logConfiguration.config;
    }

    return result;
  }

  validate() {
    /* we need 1:1 relation between input fields and input data to do proper validation */
    this.removeEmptyProperties();
    let rawInput = this.getRawInput();
    let validationErrors = utils.validate(rawInput, closureDefinitionConstraints);

    return validationErrors;
  }

  applyValidationErrors(errors) {


    errors = errors || {};

    let name = this.$el.find('.closure-name-input');
    utils.applyValidationError(name, errors.name);

    let runtime = this.$el.find('.closure-runtime-input');
    utils.applyValidationError(runtime, errors.runtime);

    let entrypoint = this.$el.find('.closure-entrypoint-input');
    utils.applyValidationError(entrypoint, errors.entrypoint);

    // var inputs = this.$el.find('.task-inputs-input');
    // utils.applyValidationError(inputs, errors.inputs);

    let sourceErrors = errors.sourceConfig || {};
    let sourceEl = this.$el.find('.closure-source-code-input');
    utils.applyValidationError(sourceEl, sourceErrors.source);

    let sourceURLEl = this.$el.find('.closure-source-url-input');
    utils.applyValidationError(sourceURLEl, sourceErrors.sourceURL);

    let memLimit = $(this.$el).find('.closure-memory-limit-input .form-control');
    utils.applyValidationError(memLimit, errors.memoryLimit);

    let timeout = $(this.$el).find('.closure-timeout-input .form-control');
    utils.applyValidationError(timeout, errors.timeout);

    this.switchTabs(errors);
  }

  switchTabs(errors) {
    let tabsToActivate = [];

    let fillTabsToActivate = ($el) => {
      let tabId = this.getTabId($el);
      if (tabsToActivate.indexOf(tabId) === -1) {
        tabsToActivate.push(tabId);
      }
    };

    if (errors.name) {
      fillTabsToActivate(this.$el.find('.closure-name-input'));
    }
    if (errors.runtime) {
      fillTabsToActivate(this.$el.find('.closure-runtime-input'));
    }
    if (errors.memoryLimit) {
      fillTabsToActivate(this.$el.find('.closure-memory-limit-input .form-control'));
    }
    if (errors.timeout) {
      fillTabsToActivate(this.$el.find('.closure-timeout-input .form-control'));
    }
    if (errors.sourceConfig) {
      fillTabsToActivate(this.$el.find('.closure-source-code-input'));
    }
    // if (errors.inputs) {
    //   fillTabsToActivate(this.$el.find('.task-inputs-input'));
    // }

    let activeTabId = this.getActiveTabId();
    if (tabsToActivate.length > 0 && (!activeTabId || tabsToActivate.indexOf(activeTabId) === -1)) {
      this.activateTab(tabsToActivate[0]);
    }
  }

  getTabId($inputEl) {
    return $inputEl.closest('.tab-pane').attr('id');
  }

  getActiveTabId() {
    return $(this.$el).find('.container-form-content .tab-content .tab-pane.active').attr('id');
  }

  activateTab(tabId) {
    $(this.$el).find('.container-form-content .nav a[href="#' + tabId + '"]').tab('show');
  }

  removeEmptyProperties() {
    for (var key in this) {
      if (this.hasOwnProperty(key)) {
        if (this[key] instanceof MulticolumnInputs) {
          this[key].removeEmptyProperties();
        }
      }
    }
  }

}

var updateForm = function(data) {
  this.$el.find('.closure-name-input .form-control').val(data.name);
  this.$el.find('.closure-description-input .form-control').val(data.description);
  if (data.runtime !== undefined) {
    this.$el.find('.closure-runtime-input .form-control').val(data.runtime);
  }
  this.$el.find('.closure-entrypoint-input .form-control').val(data.entrypoint);

  if (data.source) {
    this.sourceEditor.setValue(data.source);
  }

  this.$el.find('.closure-source-url-input .form-control').val(data.sourceURL);

  if (data.inputs) {
    let inputs = utils.objectToArray(data.inputs);
    for (var key in inputs) {
      if (inputs.hasOwnProperty(key)) {
        inputs[key].value = JSON.stringify(inputs[key].value);
      }
    }
    this.closureInputsEditor.setData(inputs);
  }

  if (data.dependencies) {
    this.dependenciesEditor.setValue(data.dependencies);

    this.$el.find('.closure-dependencies-check .checkbox-control').prop('checked', true);
    $('.closure-dependencies-input').show();
  } else {
    this.$el.find('.closure-dependencies-check .checkbox-control').prop('checked', false);
    $('.closure-dependencies-input').hide();
  }

  this.$el.find('.closure-memory-limit-input .form-control').val(data.resources.ramMB);
  this.$el.find('.closure-timeout-input .form-control').val(data.resources.timeoutSeconds);

  let sourceMode = 'Source';
  if (data.sourceURL) {
    this.$el.find('.closure-source-url-input .form-control').val(data.sourceURL);
    sourceMode = 'SourceURL';
  } else {
    this.$el.find('.closure-source-input .form-control').val(data.source);
  }
  this.$el.find('.closure-source-mode-input input[value=' + sourceMode + ']')
    .prop('checked', true);

  sourceModeChanged(sourceMode);
  updateCodeFormater(data.runtime, this.sourceEditor);
  updateDependenciesFormater(data.runtime, this.dependenciesEditor);

  if (data.logConfiguration) {
    this.$el.find('.closure-logconfig-driver-input .form-control').val(data.logConfiguration.type);
    let logConfigOptions = utils.objectToArray(data.logConfiguration.config);
    this.logConfigOptionsEditor.setData(logConfigOptions);
  }

  if (data.outputNames) {
    let outputObj = data.outputNames.reduce(function(result, item) {
      result[item] = item;
      return result;
    }, {});
    let outputNames = utils.objectToArray(outputObj);
    this.closureOutputsEditor.setData(outputNames);
  }
  if (data.inputNames) {
    let inputObj = data.inputNames.reduce(function(result, item) {
      result[item] = '';
      return result;
    }, {});
    let inputNames = utils.objectToArray(inputObj);
    this.closureInputsEditor.setData(inputNames);
  }
};

function validateNameInput(e) {
  let nameVal = $('.closure-name-input .form-control').val();
  if (!nameVal || validator.trim(nameVal).length === 0) {
    e.preventDefault();
    e.stopImmediatePropagation();
    utils.applyValidationError($('.closure-name-input'), 'errors.required');
  } else {
    utils.applyValidationError($('.closure-name-input'), null);
  }
}

function enhanceLabels(baseLabelPath, baseObject) {
  if (!baseObject) {
    baseObject = {};
  }
  baseObject.header = i18n.t(baseLabelPath);
  baseObject.placeholder = i18n.t(baseLabelPath + 'Hint');
  return baseObject;
}

function sourceModeChanged(mode) {
  if (mode === 'Source') {
    $('.closure-source-code-input').show();
    $('.closure-source-url-input').hide();
  } else if (mode === 'SourceURL') {
    $('.closure-source-code-input').hide();
    $('.closure-source-url-input').show();
  }
}

function updateCodeFormater(runtime, sourceEditor) {
  if (runtime === 'python') {
    sourceEditor.getSession().setMode({
      path: 'ace/mode/python',
      v: Date.now()
    });
  } else if (runtime === 'nodejs') {
    sourceEditor.getSession().setMode({
      path: 'ace/mode/javascript',
      v: Date.now()
    });
  }
}

function updateDependenciesFormater(runtime, dependencyEditor) {
  if (runtime === 'python') {
    dependencyEditor.getSession().setMode({
      path: 'ace/mode/text',
      v: Date.now()
    });
  } else if (runtime === 'nodejs') {
    dependencyEditor.getSession().setMode({
      path: 'ace/mode/json',
      v: Date.now()
    });
  }
}


export
default ClosureDefinitionForm;
