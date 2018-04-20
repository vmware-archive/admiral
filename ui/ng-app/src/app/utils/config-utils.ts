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

/**
 * General configuration propertis utility.
 */
export class ConfigUtils {

    private static configurationProperties;

    public static initializeConfigurationProperties(props) {
        if (this.configurationProperties) {
            throw new Error('Properties already set');
        }
        this.configurationProperties = props;
    }

    public static getConfigurationProperty(property) {
        return this.configurationProperties && this.configurationProperties[property];
    }

    public static getConfigurationProperties() {
        return this.configurationProperties;
    }

    public static getConfigurationPropertyBoolean(property) {
        return this.configurationProperties && this.configurationProperties[property] === 'true';
    }

    public static existsConfigurationProperty(property) {
        return this.configurationProperties.hasOwnProperty(property);
    }
}