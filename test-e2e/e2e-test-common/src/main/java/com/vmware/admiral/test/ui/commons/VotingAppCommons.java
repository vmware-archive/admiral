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

package com.vmware.admiral.test.ui.commons;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

import java.time.Duration;
import java.util.logging.Logger;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPageLibrary;
import com.vmware.admiral.test.ui.pages.templates.create.EditTemplatePage;

public class VotingAppCommons {

    private static final Logger LOG = Logger.getLogger(VotingAppCommons.class.getName());

    public static final String NETWORK_NAME_BACK_TIER = "back-tier";
    public static final String NETWORK_NAME_FRONT_TIER = "front-tier";
    public static final String VOLUME_NAME_DB_DATA = "db-data";
    public static final String VOLUME_DRIVER_LOCAL = "local";

    public static final String RESULT_CONTAINER_IMAGE = "eesprit/voting-app-result";
    public static final String RESULT_CONTAINER_NAME = "result";

    public static final String WORKER_CONTAINER_IMAGE = "eesprit/voting-app-worker";
    public static final String WORKER_CONTAINER_NAME = "worker";

    public static final String VOTE_CONTAINER_IMAGE = "eesprit/voting-app-vote";
    public static final String VOTE_CONTAINER_NAME = "vote";

    public static final String REDIS_CONTAINER_IMAGE = "library/redis";
    public static final String REDIS_CONTAINER_TAG = "alpine";
    public static final String REDIS_CONTAINER_NAME = "redis";

    public static final String DB_CONTAINER_IMAGE = "library/postgres";
    public static final String DB_CONTAINER_TAG = "9.4";
    public static final String DB_CONTAINER_NAME = "db";

    public static void createVotingAppTemplate(CommonWebClient<?> client, String templateName) {

        TemplatesPageLibrary templates = client.templates();
        templates.templatesPage().waitToLoad();
        templates.templatesPage().clickCreateTemplateButton();
        templates.createTemplatePage().setName(templateName);
        templates.createTemplatePage().clickProceedButton();
        EditTemplatePage editTemplate = templates.editTemplatePage();

        editTemplate.waitToLoad();
        editTemplate.clickAddNetworkButton();
        templates.addNetworkPage().setName(NETWORK_NAME_BACK_TIER);
        templates.addNetworkPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddNetworkButton();
        templates.addNetworkPage().setName(NETWORK_NAME_FRONT_TIER);
        templates.addNetworkPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddVolumeButton();
        templates.addVolumePage().setName(VOLUME_NAME_DB_DATA);
        templates.addVolumePage().setDriver(VOLUME_DRIVER_LOCAL);
        templates.addVolumePage().submit();

        editTemplate.waitToLoad();
        addContainer(client, RESULT_CONTAINER_IMAGE);

        // editTemplate.clickAddContainerButton();
        // templates.selectImagePage().waitToLoad();
        // templates.selectImagePage().searchForImage(RESULT_CONTAINER_IMAGE);
        // templates.selectImagePage().selectImageByName(RESULT_CONTAINER_IMAGE);
        templates.basicTab().setName(RESULT_CONTAINER_NAME);
        templates.basicTab().addCommand("nodemon --debug server.js");

        templates.addContainerPage().clickNetworkTab();
        templates.networkTab().addPortBinding(null, "80");
        templates.networkTab().addPortBinding(null, "5858");
        templates.networkTab().linkNetwork(NETWORK_NAME_FRONT_TIER, null, null, null);
        templates.networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates.addContainerPage().submit();

        editTemplate.waitToLoad();
        addContainer(client, WORKER_CONTAINER_IMAGE);

        // editTemplate.clickAddContainerButton();
        // templates.selectImagePage().waitToLoad();
        // templates.selectImagePage().searchForImage(WORKER_CONTAINER_IMAGE);
        // templates.selectImagePage().selectImageByName(WORKER_CONTAINER_IMAGE);
        templates.basicTab().setName(WORKER_CONTAINER_NAME);
        templates.addContainerPage().clickNetworkTab();
        templates.networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates.addContainerPage().submit();

        editTemplate.waitToLoad();
        addContainer(client, VOTE_CONTAINER_IMAGE);

        // editTemplate.clickAddContainerButton();
        // templates.selectImagePage().waitToLoad();
        // templates.selectImagePage().searchForImage(VOTE_CONTAINER_IMAGE);
        // templates.selectImagePage().selectImageByName(VOTE_CONTAINER_IMAGE);
        templates.basicTab().setName(VOTE_CONTAINER_NAME);
        templates.basicTab().addCommand("python app.py");

        templates.addContainerPage().clickNetworkTab();
        templates.networkTab().addPortBinding(null, "80");
        templates.networkTab().linkNetwork(NETWORK_NAME_FRONT_TIER, null, null, null);
        templates.networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates.addContainerPage().submit();

        editTemplate.waitToLoad();
        addContainer(client, REDIS_CONTAINER_IMAGE);

        // editTemplate.clickAddContainerButton();
        // templates.selectImagePage().waitToLoad();
        // templates.selectImagePage().searchForImage(REDIS_CONTAINER_IMAGE);
        // templates.selectImagePage().selectImageByName(REDIS_CONTAINER_IMAGE);
        templates.basicTab().setTag(REDIS_CONTAINER_TAG);
        templates.basicTab().setName(REDIS_CONTAINER_NAME);

        templates.addContainerPage().clickNetworkTab();
        templates.networkTab().addPortBinding(null, "6379");
        templates.networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates.addContainerPage().submit();

        editTemplate.waitToLoad();
        addContainer(client, DB_CONTAINER_IMAGE);

        // editTemplate.clickAddContainerButton();
        // templates.selectImagePage().waitToLoad();
        // templates.selectImagePage().searchForImage(DB_CONTAINER_IMAGE);
        // templates.selectImagePage().selectImageByName(DB_CONTAINER_IMAGE);
        templates.basicTab().setTag(DB_CONTAINER_TAG);
        templates.basicTab().setName(DB_CONTAINER_NAME);
        templates.addContainerPage().clickNetworkTab();
        templates.networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates.addContainerPage().clickStorageTab();
        templates.storageTab().addVolume("db-data", "/var/lib/postgresql/data", false);
        templates.addContainerPage().submit();

        templates.editTemplatePage().waitToLoad();
        templates.editTemplatePage().navigateBack();
    }

    // sometimes when searching for an image the spinner appears and does not disappear
    // so we retry
    private static void addContainer(CommonWebClient<?> client, String imageName) {
        client.templates().editTemplatePage().clickAddContainerButton();
        client.templates().selectImagePage().waitToLoad();
        int retriesCount = 3;
        TimeoutException ex = null;
        while (retriesCount > 0) {
            try {
                client.templates().selectImagePage().searchForImage(imageName);
                client.templates().selectImagePage().selectImageByName(imageName);
                return;
            } catch (TimeoutException e) {
                LOG.warning("Spinner did not disappear when searching for image, retrying...");
                ex = e;
                retriesCount--;
            }
        }
        LOG.severe(String.format(
                "Spinner did not disappear when searching for an image after [%d] retries",
                retriesCount));
        throw ex;
    }

    public static void voteAndVerify(String votingTarget, String resultTarget) {
        LOG.info("Opening voting app voting page");
        open(votingTarget);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e1) {
        }
        LOG.info("Voting for cats");
        $(By.cssSelector("#a")).click();
        LOG.info("Opening the votig app results page");
        open(resultTarget);
        LOG.info("Validating voting results");
        $(By.cssSelector(".choice.cats .stat.ng-binding")).should(Condition.exist);
        try {
            com.codeborne.selenide.Selenide.Wait()
                    .withTimeout(Duration.ofSeconds(10))
                    .until(d -> {
                        return $(By.cssSelector("#result>span"))
                                .has(Condition.exactText("1 vote")) &&
                                $(By.cssSelector(".choice.cats .stat.ng-binding"))
                                        .has(Condition.exactText("100.0%"));
                    });
        } catch (TimeoutException e) {
            String message = "Voting app results page did not show correct results";
            throw new IllegalStateException(message);
        }
    }

}
