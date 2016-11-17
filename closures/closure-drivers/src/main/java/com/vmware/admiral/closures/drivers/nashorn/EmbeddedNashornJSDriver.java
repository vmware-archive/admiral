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

package com.vmware.admiral.closures.drivers.nashorn;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Execution driver which uses 'Nashorn' implementation
 */
public class EmbeddedNashornJSDriver extends LocalDriverBase {

    private final ServiceHost serviceHost;

    public interface JsDateWrap {
        long getTime();
    }

    public EmbeddedNashornJSDriver(ServiceHost serviceHost) {
        this.serviceHost = serviceHost;
    }

    @Override
    public ServiceHost getServiceHost() {
        return serviceHost;
    }

    @Override
    public void cleanImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler) {
        Utils.logWarning("Not implemented");
        errorHandler.accept(new Exception("Not implemented"));
    }

    @Override
    public void inspectImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler) {
        Utils.logWarning("Not implemented");
        errorHandler.accept(new Exception("Not implemented"));
    }

    @Override
    public Closure doExecute(Closure closure, ClosureDescription taskDef) {
        logInfo("Submitting closure for execution: " + closure.documentSelfLink);

        Closure closureResult = new Closure();

        Map<String, JsonElement> outputs = new HashMap<>();
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName(DriverConstants.RUNTIME_NASHORN);
        if (engine == null) {
            throw new IllegalStateException("Unable to execute script with runtime: " + taskDef.runtime);
        }

        try {
            setBindings(closure, engine);
            executeScript(closure, taskDef, engine);
            closureResult.state = TaskStage.FINISHED;

        } catch (ScriptException e) {
            Utils.logWarning("Exception thrown while executing script: " + e.getMessage());
            closureResult.state = TaskStage.FAILED;
            closureResult.errorMsg = e.getMessage();
        }

        // populate outputs
        populateOutputs(engine, taskDef.outputNames, outputs);
        closureResult.outputs = outputs;
        return closureResult;

    }

    private void populateOutputs(ScriptEngine engine, List<String> outputNames, Map<String, JsonElement> outputs) {
        if (outputNames != null) {
            final Bindings outBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            for (String name : outputNames) {
                Object val = outBindings.get(name);
                logInfo("Output parameter: name: {} value: {}", name, val);
                JsonElement convertedVal = convertToJsonElement(engine, val);
                outputs.put(name, convertedVal);
            }
        }
    }

    private void executeScript(Closure closureRequest, ClosureDescription taskDef, ScriptEngine engine)
            throws ScriptException {
        String scriptSource = taskDef.source;
        ResourceConstraints resConstraints = taskDef.resources;
        logInfo("Using resource constraints: cpuShares = {}, ram = {}, timeout = {}", resConstraints.cpuShares,
                resConstraints.ramMB, resConstraints.timeoutSeconds);
        logInfo("Executing script of {}:\n{}", closureRequest.documentSelfLink, scriptSource);

        engine.eval(scriptSource);
    }

    private void setBindings(Closure closureRequest, ScriptEngine engine) throws ScriptException {
        final Bindings inBindings = engine.createBindings();

        inBindings.put("result", null);
        Map<String, JsonElement> inputs = closureRequest.inputs;
        JsonObject element = new JsonObject();
        if (inputs != null) {
            inputs.forEach((k, v) -> {
                element.add(k, v);
            });
            inBindings.put("inputs", convertValue(engine, element));
        }

        engine.setBindings(inBindings, ScriptContext.ENGINE_SCOPE);
    }

    private Object convertValue(ScriptEngine engine, JsonElement var) throws ScriptException {
        return engine.eval("JSON.parse('" + var.toString() + "')", engine.getContext());
    }

    @SuppressWarnings({ "restriction", "unchecked" })
    private JsonElement convertToJsonElement(ScriptEngine engine, Object val) {

        if (val == null) {
            return JsonNull.INSTANCE;
        } else if (val instanceof String) {
            return new JsonPrimitive((String) val);
        } else if (val instanceof Number) {
            return new JsonPrimitive((Number) val);
        } else if (val instanceof Boolean) {
            return new JsonPrimitive((Boolean) val);
        } else if (val instanceof Object[]) {
            JsonArray jsArray = new JsonArray();
            Object[] nums = (Object[]) val;
            for (Object n : nums) {
                jsArray.add(convertToJsonElement(engine, n));
            }
            return jsArray;
        } else if (val instanceof List) {
            JsonArray jsArray = new JsonArray();
            List<Object> objs = (List<Object>) val;
            for (Object o : objs) {
                jsArray.add(convertToJsonElement(engine, o));
            }
            return jsArray;
        } else if (val instanceof ScriptObjectMirror) {
            ScriptObjectMirror m = (ScriptObjectMirror) val;
            if (m.isArray()) {
                List<Object> list = m.values().stream().collect(Collectors.toList());
                return convertToJsonElement(engine, list);
            } else {
                JsDateWrap jsDate = ((Invocable) engine).getInterface(val, JsDateWrap.class);
                if (jsDate != null) {
                    long timestampLocalTime = jsDate.getTime();
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(timestampLocalTime);
                    val = calendar.getTime();
                }
                String stringified = Utils.toJson(val);
                return Utils.fromJson(stringified, JsonElement.class);
            }
        }

        return new JsonPrimitive(val.toString());
    }
}
