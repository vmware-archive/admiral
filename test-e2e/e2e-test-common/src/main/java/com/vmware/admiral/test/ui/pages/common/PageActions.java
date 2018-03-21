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

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;
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
    }

    public void click(By locator, int xOffsetFromTopLeft, int yOffsetFromTopLeft) {
        switchToFrame();
        $(locator).click(xOffsetFromTopLeft, yOffsetFromTopLeft);
    }

    public void hover(By locator) {
        switchToFrame();
        $(locator).hover();
    }

    public boolean isDisplayed(By locator) {
        switchToFrame();
        return $(locator).isDisplayed();
    }

    public String getAttribute(String attribute, By locator) {
        switchToFrame();
        return $(locator).getAttribute(attribute);
    }

    public String getText(By locator) {
        switchToFrame();
        return $(locator).getText();
    }

    public void sendKeys(String keys, By locator) {
        switchToFrame();
        $(locator).sendKeys(keys);
    }

    public void clear(By locator) {
        switchToFrame();
        $(locator).clear();
    }

    public void selectOptionByValue(String value, By locator) {
        switchToFrame();
        $(locator).selectOptionByValue(value);
    }

    public void selectOptionByText(String text, By locator) {
        switchToFrame();
        $(locator).selectOption(text);
    }

    public int getElementCount(By locator) {
        switchToFrame();
        return getElements(locator).size();
    }

    public void uploadFile(File file, By locator) {
        switchToFrame();
        $(locator).uploadFile(file);
    }

    public void setCheckbox(boolean checked, By locator) {
        switchToFrame();
        $(locator).setSelected(checked);
    }

    public void dragAndDrop(By from, By to) {
        switchToFrame();
        actions().dragAndDrop($(from), $(to)).build().perform();
    }

    public File donwload(String localFilePath, By locator) {
        switchToFrame();
        File file = new File(localFilePath);
        String folder = file.getParent();
        File localFolder = new File(folder);
        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new IllegalArgumentException(
                    String.format("Folder [%s] does not exist.", folder));
        }
        File remoteFile;
        try {
            remoteFile = $(locator).download();
            File localFile = new File(localFilePath);
            Files.move(remoteFile, localFile);
            return localFile;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could now download file.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "File was downloaded, but was not moved to desired diretory.", e);
        }
    }

    public Point getCoordinates(By locator) {
        switchToFrame();
        org.openqa.selenium.Point seleniumPoint = $(locator).getCoordinates().inViewPort();
        Point point = new Point();
        point.setLocation(seleniumPoint.getX(), seleniumPoint.getY());
        return point;
    }

    public Dimension getDimension(By locator) {
        switchToFrame();
        org.openqa.selenium.Dimension seleniumDimension = $(locator).getSize();
        Dimension dimension = new Dimension();
        dimension.setSize(seleniumDimension.getWidth(), seleniumDimension.getHeight());
        return dimension;
    }

    public void waitForElementToAppearAndDisappear(By element) {
        switchToFrame();
        try {
            Wait().withTimeout(3, TimeUnit.SECONDS)
                    .until(d -> $(element).is(Condition.visible));
        } catch (TimeoutException e) {
            // element is not going to appear
        }
        Wait().until(d -> $(element).is(Condition.hidden));
    }

}
