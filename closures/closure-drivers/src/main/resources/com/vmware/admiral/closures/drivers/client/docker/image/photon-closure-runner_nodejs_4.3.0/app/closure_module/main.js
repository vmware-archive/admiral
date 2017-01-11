"use strict";
/*jslint node: true, stupid: true */

var restler = require('restler');
var fs = require('fs');
var moment = require('moment');
var url = require('url');
var _ = require('lodash');
var http = require('http');
var unzip = require('unzip');
var child_process = require('child_process');

var userScriptFileName = 'index.js';
var userScriptSrcFolder = 'userSrc';
var userScriptScrPathPrefix = '../../';

var closureSemaphore = null;

function getModuleName() {
    var packageJson, jsonContent;
    try {
        packageJson = fs.readFileSync("./" + userScriptSrcFolder + "/package.json");
    } catch (e) {
        console.info("No valid package.json provided!");
        return null;
    }

    try {
        jsonContent = JSON.parse(packageJson);
        return jsonContent.name;
    } catch (ex) {
        console.error("Unable to read module name from the provided package.json!");
        throw "Unable to read valid module name: " + ex;
    }
}

function createAppPackageJson(scriptDeps) {
    var packageJson = "\{\n \
    \"name\": \"app_runner\",\n \
    \"version\": \"1.0.0\",\n \
    \"private\": \"true\",\n \
    \"dependencies\": \
    " + scriptDeps + "\n\}";

    fs.writeFileSync("./package.json", packageJson);
}

function prepareArguments(context) {
    var args = [];
    args.push(context);
    if (context.inputs) {
        Object.keys(context.inputs).forEach(function(key) {
            args.push(context.inputs[key]);
        })
    }
    return args;
}

function run(context, moduleName, handlerName) {
    if (context == null) {
        // skip execution
        return;
    }

    // clear token before running client script
    delete process.env.TOKEN;
    // console.log("Requiring JS script in: " + moduleName);
    var userModule = require(moduleName);

    var closure_args = prepareArguments(context);

    // console.log("Calling script in: " + moduleName + "->" + handlerName);
    console.log("Script log:");
    console.log("******************");
    if (userModule instanceof Function) {
        userModule.apply(null, closure_args);
    } else {
        userModule[handlerName].apply(null, closure_args);
    }
}

function executeScriptAsZIP(data, context) {
    var moduleName = getModuleName(),
        child;
    if (moduleName) {
        if (context == null) {
            console.log("Installing JS dependencies of module: " + moduleName);
            child = child_process.exec('npm install ' + "./" + userScriptSrcFolder, function(error, stdout, stderr) {
                if (error) {
                    console.log(error);
                    throw 'Unable to install dependencies: ' + error;
                }
                process.stdout.write(stdout);
                process.stderr.write(stderr);
            });
         } else {
            run(context, moduleName, data.name);
         }
    } else {
        // no package.json provided
        if (data.entrypoint) {
            var entry = data.entrypoint,
                sepIndex = entry.lastIndexOf("."),
                handlerName = entry.substring(sepIndex + 1);
            moduleName = entry.substring(0, sepIndex);
            run(context, userScriptScrPathPrefix + userScriptSrcFolder + "/" + moduleName + ".js", handlerName);
        } else {
            // no entrypoint. use name as module
            run(context, userScriptScrPathPrefix + userScriptSrcFolder, data.name);
        }
    }
}


function executeScript(scriptDeps, entrypoint, functionName, context) {
    var entryNames = getEntrypointNames(entrypoint, functionName),
        child;
    if (scriptDeps && context == null) {
        createAppPackageJson(scriptDeps);
        child = child_process.exec('npm install', function(error, stdout, stderr) {
            if (error) {
                console.log(error);
                throw 'Unable to install dependencies: ' + error;
            }
            process.stdout.write(stdout);
            process.stderr.write(stderr);

//            run(context, entryNames[0], entryNames[1]);
        });
    } else {
        run(context, entryNames[0], entryNames[1]);
    }
}

function getEntrypointNames(entrypoint, functionName) {
    if (entrypoint) {
        var sepIndex = entrypoint.lastIndexOf("."),
            moduleName = entrypoint.substring(0, sepIndex),
            handlerName = entrypoint.substring(sepIndex + 1);
        return [userScriptScrPathPrefix + moduleName + ".js", handlerName];
    }

    return [userScriptScrPathPrefix + userScriptFileName, functionName];
}


function generateModuleFileName(entrypoint) {
    var generatedModuleFileName = userScriptFileName;
    if (entrypoint) {
        var sepIndex = entrypoint.lastIndexOf("."),
            moduleName = entrypoint.substring(0, sepIndex);
        generatedModuleFileName = moduleName + ".js";
    }

    return generatedModuleFileName;
}

function proceedWithSourceUrl(data, context) {
    var URL = data.sourceURL,
        scriptDeps = data.dependencies,
        entrypoint = data.entrypoint,
        request;
    request = http.get(URL, function(response) {
        if (response.statusCode !== 200) {
            console.error("Unable to fetch closure source from URI: " + URL);

            throw 'Unable to fetch closure sources from URL: ' + URL;
        }

        var contentType = response.headers["content-type"];
        if (contentType === 'application/octet-stream' || contentType === 'application/zip') {
            response.pipe(unzip.Extract({
                path: './' + userScriptSrcFolder
            }).on('close', function() {
                console.log("File downloaded from: " + URL);

                executeScriptAsZIP(data, context);
            }).on('error', function(error) {
                throw 'Error while unpacking provided zip: ' + error;
            }));
        } else {
            var generatedModuleFileName = generateModuleFileName(entrypoint);
            var file = fs.createWriteStream(generatedModuleFileName);
            file.on('finish', function() {
                // close write stream
                file.close(function(err) {
                    executeScript(scriptDeps, data.entrypoint, data.name, context);
                });
            });
            file.on('error', function(error) {
                throw 'Error while preparing script for running: ' + error;
            });
            response.pipe(file);
        }

    });
}

function getProceedWithTaskDef(closureDescUri, internalCtx, context) {
    // console.log("Getting closure description to TASKDEF_URI = " + closureDescUri);
    var headers = {
        'x-xenon-auth-token': process.env.TOKEN
    }
    restler.get(closureDescUri, {
        headers: headers
      }).on('complete', function(data, response) {
            if (internalCtx) {
                internalCtx.outputNames = data.outputNames;
            }
            var scriptSource = data.source,
                scriptSourceURL = data.sourceURL,
                scriptDeps = data.dependencies,
                entrypoint = data.entrypoint,
                functionName = data.name,
                generatedModuleFileName;
            if (scriptSourceURL) {
                console.log("Fetching from URL: " + scriptSourceURL);
                proceedWithSourceUrl(data, context);
            } else {
                generatedModuleFileName = generateModuleFileName(entrypoint);

                fs.writeFileSync(generatedModuleFileName, scriptSource);
                // console.log('Will execute JS code:\n---');
                // console.log(scriptSource);
                // console.log('---');

                executeScript(scriptDeps, entrypoint, functionName, context);
            }
        });
}

function getClosureSemaphore() {
    return closureSemaphore;
}

function initExitCallbacks(context, internalCtx) {
    // event loop ended, must complete script
    process.on('exit', function() {
        console.log("******************");
        console.log("Script run completed at: " + moment().toDate());
        complete(context, internalCtx, getClosureSemaphore());
    });

    //error occured: complete script with error
    process.on('uncaughtException', function(error) {
        //just stash the error, exit handler will take care
        console.error("Script run failed with error: " + error);
        context.error = error;
    });
}


function buildUri(closureUri, descriptionLink) {
    var closureUrl = url.parse(closureUri);
    return closureUrl.protocol + "//" + closureUrl.host + descriptionLink;
}

function proceedWithExecution(closureUri, internalCtx, context, closure) {
    if (context == null) {
        // don't execute the script
        var closureDescUriLink = closure.descriptionLink,
        closureDescUri = buildUri(closureUri, closureDescUriLink);

        getProceedWithTaskDef(closureDescUri, internalCtx, context);
    } else {
        var runningState = {
            "state": "STARTED",
            "closureSemaphore": closure.closureSemaphore
        }
        var headers = {
            'x-xenon-auth-token': process.env.TOKEN
        }
        restler.patchJson(closureUri, runningState, {
            headers: headers
            }).on('complete', function(data, response) {
                if (response.statusCode !== 200) {
                    console.error("Unable to start closure with URI: " + closureUri);

                    process.exit(1);
                }
                // handle response
                // console.info("Entered RUNNING state for: " + closureUri);

                // Persist script source to disk
                var closureDescUriLink = closure.descriptionLink,
                    closureDescUri = buildUri(closureUri, closureDescUriLink);

                // inject inputs
                context.inputs = closure.inputs;

                getProceedWithTaskDef(closureDescUri, internalCtx, context);
            });
       }
}

function proceedWithTask(closureUri, internalCtx, context) {
    // console.log("Getting closure to execute from TASK_URI=" + closureUri);
    var headers = {
        'x-xenon-auth-token': process.env.TOKEN
    }
    restler.get(closureUri, {
        headers: headers
      }).on('complete', function(data, response) {
            if (!response || response.statusCode !== 200) {
                var msg = "Unable to get closure source from URI: " + closureUri;
                if (response) {
                    msg += " code: " + response.statusCode;
                }
                console.error(msg);
                throw msg;
            }

            // semaphore fetched ...assign the value
            closureSemaphore = data.closureSemaphore;
            proceedWithExecution(closureUri, internalCtx, context, data);
        });
}

function js2rest(obj) {
    if (_.isDate(obj)) {
        return moment.utc(obj);
    }
    return obj;
}


function complete(context, internalCtx, closureSemaphore, error) {
    var response = {
        state: 'FINISHED',
        closureSemaphore: closureSemaphore,
        outputs: {}
    };

    if (!error) {
        error = context.error;
    }
    if (error) {
        response.state = 'FAILED';
        if (_.isError(error)) {
            response.errorMsg = error.stack;
        } else {
            response.errorMsg = error.toString();
        }
    }

    if (internalCtx.outputNames) {
        _.forEach(internalCtx.outputNames, function(outName) {
//            console.log("Populating value output name: " + outName);
            if (context.outputs) {
                response.outputs[outName] = js2rest(context.outputs[outName]);
            }
        });
    }

    var jsonResult = JSON.stringify(response, 2, '  ');
    console.log("Script run state: ", response.state);

    fs.writeFileSync('response.json', jsonResult, 'utf-8');
}

function install_dependencies() {
    console.log("Installing dependencies started at: " + moment().toDate());

    var closureUri = process.env.TASK_URI;
    if (!closureUri) {
        console.warn("TASK_URI env variable not set. Dependencies installation aborted.");
        return;
    }
    proceedWithTask(closureUri, null, null);
    console.log("Installing of dependencies done at: " + moment().toDate());
}

function executeFunc(token, link, operation, body, handler) {
    var headers = {
        'x-xenon-auth-token': token
    }
    if (!operation) {
        throw '\'operation\' parameter on ctx.execute() is not provided!';
    }
    if (!link) {
        throw '\'link\' parameter on ctx.execute() is not provided!';
    }
    let op = _.toUpper(operation);
    let targetUri = buildUri(process.env.TASK_URI, link);
    if (_.isEqual(op, 'GET')) {
        restler.get(targetUri, {
            headers: headers
        }).on('complete', arguments.length > 4 ? handler : body);
    } else if (_.isEqual(op, 'PATCH')) {
        restler.patch(targetUri, body, {
            headers: headers
        }).on('complete', handler);
    } else if (_.isEqual(op, 'POST')) {
        restler.post(targetUri, body, {
            headers: headers
        }).on('complete', handler);
    } else if (_.isEqual(op, 'PUT')) {
        restler.post(targetUri, body, {
            headers: headers
        }).on('complete', handler);
    } else {
        throw 'Unsupported operation on ctx.execute() function: ' + operation;
    }
}

function main() {
    console.log("Script run started at: " + moment().toDate());
    let token = process.env.TOKEN;
    var context = {
        outputs: {},
        execute: function() {
           var args = Array.prototype.slice.call(arguments);
           args.splice(0, 0, token);
           return executeFunc.apply(null, args);
        }
    };
    var internalCtx = {
        outputNames: []
    };
    initExitCallbacks(context, internalCtx);
    var closureUri = process.env.TASK_URI;
    if (!closureUri) {
        console.warn("TASK_URI env variable not set. Script execution aborted.");
        throw 'TASK_URI env variable not set. Script execution aborted.';
    }
    proceedWithTask(closureUri, internalCtx, context);
}

module.exports.run = main;
module.exports.install_dependencies = install_dependencies;
