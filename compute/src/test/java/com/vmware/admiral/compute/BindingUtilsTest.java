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

package com.vmware.admiral.compute;

import static junit.framework.TestCase.assertEquals;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.compute.content.Binding;


@SuppressWarnings({"serial", "rawtypes"})
public class BindingUtilsTest {

    @Test
    public void extractBindingsSimple() {
        final String componentName = "component-name";
        //CHECKSTYLE:OFF
        Map<String, Object> input = new HashMap<String, Object>() {{
            put(BindingUtils.COMPONENTS, new HashMap<String, Object>() {{
                put(componentName, new HashMap<String, Object>() {{
                    put(BindingUtils.DATA, new HashMap<String, Object>() {{
                        put("field1", 3);
                        put("field2", "${component1~field1}");
                    }});
                }});
            }});
        }};
        //CHECKSTYLE:ON

        List<Binding.ComponentBinding> componentBindings = BindingUtils.extractBindings(input);
        assertEquals(1, componentBindings.size());
        //the binding should be removed from the map
        assertEquals(1,
                ((Map) ((Map) ((Map) input.get(BindingUtils.COMPONENTS)).get(componentName))
                        .get(BindingUtils.DATA))
                        .size());

        assertEquals(componentName, componentBindings.get(0).componentName);
        assertEquals("component1~field1",
                componentBindings.get(0).bindings.get(0).placeholder.bindingExpression);
    }

    @Test
    public void extractBindingsSimpleProvisioningTimeBinding() {
        final String componentName = "component-name";
        //CHECKSTYLE:OFF
        Map<String, Object> input = new HashMap<String, Object>() {{
            put(BindingUtils.COMPONENTS, new HashMap<String, Object>() {{
                put(componentName, new HashMap<String, Object>() {{
                    put(BindingUtils.DATA, new HashMap<String, Object>() {{
                        put("field1", 3);
                        put("field2", "${_resource~component1~field1}");
                    }});
                }});
            }});
        }};
        //CHECKSTYLE:ON

        List<Binding.ComponentBinding> componentBindings = BindingUtils.extractBindings(input);
        assertEquals(1, componentBindings.size());
        //the binding should be removed from the map
        assertEquals(1,
                ((Map) ((Map) ((Map) input.get(BindingUtils.COMPONENTS)).get(componentName))
                        .get(BindingUtils.DATA))
                        .size());

        assertEquals(componentName, componentBindings.get(0).componentName);
        assertEquals("_resource~component1~field1",
                componentBindings.get(0).bindings.get(0).placeholder.bindingExpression);
        assertTrue(componentBindings.get(0).bindings.get(0).isProvisioningTimeBinding());
    }

    @Test
    public void extractBindingsNested() {
        final String componentName = "component-name";
        //CHECKSTYLE:OFF
        Map<String, Object> input = new HashMap<String, Object>() {{
            put(BindingUtils.COMPONENTS, new HashMap<String, Object>() {{
                put(componentName, new HashMap<String, Object>() {{
                    put(BindingUtils.DATA, new HashMap<String, Object>() {{
                        put("field1", 3);
                        put("field2", new HashMap<String, Object>() {{
                            put("field21", "${component1~field1}");
                        }});
                    }});
                }});
            }});
        }};
        //CHECKSTYLE:ON

        List<Binding.ComponentBinding> componentBindings = BindingUtils.extractBindings(input);
        assertEquals(1, componentBindings.size());
        //the binding should be removed from the map
        assertEquals(0,
                ((Map) ((Map) ((Map) ((Map) input.get(BindingUtils.COMPONENTS)).get(componentName))
                        .get(BindingUtils.DATA)).get("field2")).size());

        assertEquals(componentName, componentBindings.get(0).componentName);
        assertEquals("component1~field1",
                componentBindings.get(0).bindings.get(0).placeholder.bindingExpression);

        assertEquals(Arrays.asList("field2", "field21"),
                componentBindings.get(0).bindings.get(0).targetFieldPath);
    }

    @Test
    public void extractBindingsNestedList() {
        final String componentName = "component-name";
        //CHECKSTYLE:OFF
        Map<String, Object> input = new HashMap<String, Object>() {{
            put(BindingUtils.COMPONENTS, new HashMap<String, Object>() {{
                put(componentName, new HashMap<String, Object>() {{
                    put(BindingUtils.DATA, new HashMap<String, Object>() {{
                        put("field1", 3);
                        put("field2", new ArrayList<Object>() {{
                            add("stuff");
                            add("${component1~field1}");
                            add("more stuff");
                        }});
                    }});
                }});
            }});
        }};
        //CHECKSTYLE:ON

        List<Binding.ComponentBinding> componentBindings = BindingUtils.extractBindings(input);
        assertEquals(1, componentBindings.size());
        //the binding should be removed from the List
        assertEquals(2,
                ((List) ((Map) ((Map) ((Map) input.get(BindingUtils.COMPONENTS)).get(componentName))
                        .get(BindingUtils.DATA)).get("field2")).size());

        assertEquals(componentName, componentBindings.get(0).componentName);
        assertEquals("component1~field1",
                componentBindings.get(0).bindings.get(0).placeholder.bindingExpression);

        assertEquals(Arrays.asList("field2", "1"),
                componentBindings.get(0).bindings.get(0).targetFieldPath);
    }

}
