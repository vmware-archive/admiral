/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.Path;

public class ReflectionUtils {

    private static final Logger logger = Logger.getLogger(ReflectionUtils.class.getName());

    private static final String ANNOTATION_METHOD = "annotationData";
    private static final String ANNOTATIONS = "annotations";

    private ReflectionUtils() {
    }

    @SuppressWarnings("unchecked")
    public static void setAnnotation(Class<?> targetClass,
            Class<? extends Annotation> targetAnnotation, Annotation targetValue) {
        AssertUtil.assertNotNull(targetClass, "targetClass");
        AssertUtil.assertNotNull(targetAnnotation, "targetAnnotation");
        AssertUtil.assertNotNull(targetValue, "targetValue");
        try {
            Method method = Class.class.getDeclaredMethod(ANNOTATION_METHOD);
            method.setAccessible(true);

            Object annotationData = method.invoke(targetClass);

            Field annotations = annotationData.getClass().getDeclaredField(ANNOTATIONS);
            annotations.setAccessible(true);

            Map<Class<? extends Annotation>, Annotation> map = (Map<Class<? extends Annotation>, Annotation>) annotations
                    .get(annotationData);
            map.put(targetAnnotation, targetValue);
        } catch (ReflectiveOperationException e) {
            logger.warning(String.format("Could not set class %s annotation %s to value %s!",
                    targetClass.getSimpleName(), targetAnnotation.getSimpleName(),
                    targetValue.getClass().getSimpleName()));
        }
    }

    public static class CustomPath implements Path {

        private final String value;

        public CustomPath(String value) {
            this.value = value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return CustomPath.class;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
