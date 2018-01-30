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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.actions;
import static com.codeborne.selenide.Selenide.switchTo;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.google.common.io.Files;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

public class PageActions extends Action {

    public PageActions(By[] iframeLocators) {
        super(iframeLocators);
    }

    public void click(By locator) {
        switchToFrame();
        $(locator).click();
        switchTo().defaultContent();
    }

    public void click(By locator, int xOffsetFromTopLeft, int yOffsetFromTopLeft) {
        switchToFrame();
        $(locator).click(xOffsetFromTopLeft, yOffsetFromTopLeft);
        switchTo().defaultContent();
    }

    public void hover(By locator) {
        switchToFrame();
        $(locator).hover();
        switchTo().defaultContent();
    }

    public boolean isDisplayed(By locator) {
        switchToFrame();
        SelenideElement element = $(locator);
        boolean isDisplayed = element.isDisplayed();
        switchTo().defaultContent();
        return isDisplayed;
    }

    public String getAttribute(String attribute, By locator) {
        switchToFrame();
        SelenideElement element = $(locator);
        String value = element.getAttribute(attribute);
        switchTo().defaultContent();
        return value;
    }

    public String getText(By locator) {
        switchToFrame();
        SelenideElement element = $(locator);
        String text = element.getText();
        switchTo().defaultContent();
        return text;
    }

    public void sendKeys(String keys, By locator) {
        switchToFrame();
        $(locator).sendKeys(keys);
        switchTo().defaultContent();
    }

    public void clear(By locator) {
        switchToFrame();
        $(locator).clear();
        switchTo().defaultContent();
    }

    public void selectOptionByValue(String value, By locator) {
        switchToFrame();
        $(locator).selectOptionByValue(value);
        switchTo().defaultContent();
    }

    public int getElementCount(By locator) {
        switchToFrame();
        int count = getElements(locator).size();
        switchTo().defaultContent();
        return count;
    }

    public void uploadFile(File file, By locator) {
        switchToFrame();
        $(locator).uploadFile(file);
        switchTo().defaultContent();
    }

    public void setCheckbox(boolean checked, By locator) {
        switchToFrame();
        $(locator).setSelected(checked);
        switchTo().defaultContent();
    }

    public void dragAndDrop(By from, By to) {
        switchToFrame();
        actions().dragAndDrop($(from), $(to)).build().perform();
        switchTo().defaultContent();
    }

    public void donwload(String localFile, By locator) {
        switchToFrame();
        File file = new File(localFile);
        String folder = file.getParent();
        File localFolder = new File(folder);
        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new IllegalArgumentException(
                    String.format("Folder [%s] does not exist.", folder));
        }
        File remoteFile;
        try {
            remoteFile = $(locator).download();
            Files.move(remoteFile, new File(localFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could now download file.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "File was downloaded, but was not moved to desired diretory.", e);
        }
        switchTo().defaultContent();
    }

    public Point getCoordinates(By locator) {
        switchToFrame();
        org.openqa.selenium.Point seleniumPoint = $(locator).getCoordinates().inViewPort();
        Point point = new Point();
        point.setLocation(seleniumPoint.getX(), seleniumPoint.getY());
        switchTo().defaultContent();
        return point;
    }

    public Dimension getDimesion(By locator) {
        switchToFrame();
        org.openqa.selenium.Dimension seleniumDimension = $(locator).getSize();
        Dimension dimension = new Dimension();
        dimension.setSize(seleniumDimension.getWidth(), seleniumDimension.getHeight());
        switchTo().defaultContent();
        return dimension;
    }

    public void waitForElementToAppearAndDisappear(By element) {
        switchToFrame();
        try {
            Wait().withTimeout(3, TimeUnit.SECONDS)
                    .until(d -> {
                        return $(element).is(Condition.visible);
                    });
        } catch (TimeoutException e) {
            // element is not going to appear
        }
        Wait().until(d -> {
            return $(element).is(Condition.hidden);
        });
        switchTo().defaultContent();
    }

}
