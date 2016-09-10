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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for the {@link ElasticPlacementZoneMonitor} class.
 */
public class ElasticPlacementZoneMonitorTest extends ComputeBaseTest {
    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ElasticPlacementZoneMonitor.SELF_LINK);
    }

    @Test
    public void testEpz() throws Throwable {
        // create EPZs
        createEpz("rp1Link", "tag1Link", "tag2Link"); // no matching computes
        createEpz("rp2Link", "tag3Link", "tag4Link"); // compute3 should match
        createEpz("rp3Link", "tag5Link");             // compute4 should match
        waitForMonitorStart();

        // create computes
        String compute1Link = createCompute("rp0Link", "tag1Link").documentSelfLink;
        String compute2Link = createCompute(null, "tag2Link").documentSelfLink;
        String compute3Link = createCompute(null, "tag3Link", "tag4Link").documentSelfLink;
        String compute4Link = createCompute("rp1link", "tag5Link", "tag6Link").documentSelfLink;

        // invoke elastic placement zone monitor
        runMonitorOnce();

        ComputeService.ComputeState compute1 =
                getDocument(ComputeService.ComputeState.class, compute1Link);
        ComputeService.ComputeState compute2 =
                getDocument(ComputeService.ComputeState.class, compute2Link);
        ComputeService.ComputeState compute3 =
                getDocument(ComputeService.ComputeState.class, compute3Link);
        ComputeService.ComputeState compute4 =
                getDocument(ComputeService.ComputeState.class, compute4Link);

        assertEquals("rp0Link", compute1.resourcePoolLink);
        assertEquals(null, compute2.resourcePoolLink);
        assertEquals("rp2Link", compute3.resourcePoolLink);
        assertEquals("rp3Link", compute4.resourcePoolLink);
    }

    @Test
    public void testEpzConflict() throws Throwable {
        // create EPZs
        createEpz("rp1Link", "tag1Link");
        createEpz("rp2Link", "tag2Link");
        waitForMonitorStart();

        // create computes
        String compute1Link = createCompute(null, "tag1Link", "tag2Link").documentSelfLink;
        String compute2Link = createCompute("rp0Link", "tag1Link", "tag2Link", "tag3Link")
                .documentSelfLink;

        // invoke elastic placement zone monitor
        runMonitorOnce();

        ComputeService.ComputeState compute1 =
                getDocument(ComputeService.ComputeState.class, compute1Link);
        ComputeService.ComputeState compute2 =
                getDocument(ComputeService.ComputeState.class, compute2Link);

        // make sure no RP changes were done
        assertEquals(null, compute1.resourcePoolLink);
        assertEquals("rp0Link", compute2.resourcePoolLink);
    }

    @Test
    public void testEpzConflictAlreadyAssigned() throws Throwable {
        // create EPZs
        createEpz("rp1Link", "tag1Link");
        createEpz("rp2Link", "tag2Link");
        waitForMonitorStart();

        // create computes
        String compute1Link = createCompute("rp1Link", "tag1Link", "tag2Link").documentSelfLink;
        String compute2Link = createCompute("rp2Link", "tag1Link", "tag2Link", "tag3Link")
                .documentSelfLink;

        // invoke elastic placement zone monitor
        runMonitorOnce();

        ComputeService.ComputeState compute1 =
                getDocument(ComputeService.ComputeState.class, compute1Link);
        ComputeService.ComputeState compute2 =
                getDocument(ComputeService.ComputeState.class, compute2Link);

        // make sure no RP changes were done
        assertEquals("rp1Link", compute1.resourcePoolLink);
        assertEquals("rp2Link", compute2.resourcePoolLink);
    }

    @Test
    public void testAutoStartStop() throws Throwable {
        // make sure that the monitor automatically starts when EPZ is created
        assertFalse(isMonitorStarted());
        String epzLink = createEpz("rp1Link", "tag1Link").documentSelfLink;
        waitForMonitorStart();

        // stop the monitor before the next operation
        stopMonitor();
        assertFalse(isMonitorStarted());

        // make sure the monitor automatically stops when there are no EPZs
        delete(epzLink);
        startMonitor();
        waitFor(() -> !isMonitorStarted());
    }

    private ElasticPlacementZoneState createEpz(String rpLink, String... tagLinks)
            throws Throwable {
        ElasticPlacementZoneState initialState = new ElasticPlacementZoneState();
        initialState.resourcePoolLink = rpLink;
        initialState.tagLinksToMatch = tagSet(tagLinks);
        return doPost(initialState, ElasticPlacementZoneService.FACTORY_LINK);
    }

    private ComputeService.ComputeState createCompute(String rpLink, String... tagLinks)
            throws Throwable {
        ComputeService.ComputeState initialState = new ComputeService.ComputeState();
        initialState.descriptionLink = "dummy-desc";
        initialState.resourcePoolLink = rpLink;
        initialState.tagLinks = tagSet(tagLinks);
        return doPost(initialState, ComputeService.FACTORY_LINK);
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }

    /**
     * Starts the monitor synchronously. Nothing is done if already started.
     * Otherwise the method will exit after one monitoring execution completes.
     */
    private void startMonitor() {
        runSynchronously(ch -> ElasticPlacementZoneMonitor.start(host, host.getReferer(), ch));
    }

    /**
     * Stops the monitor synchronously.
     */
    private void stopMonitor() {
        runSynchronously(ch -> ElasticPlacementZoneMonitor.stop(host, host.getReferer(), ch));
    }

    /**
     * Waits for the monitor to be started, i.e. the scheduled task to be created (which includes
     * its first execution).
     */
    private void waitForMonitorStart() throws Throwable {
        waitForServiceAvailability(UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK,
                ElasticPlacementZoneMonitor.SCHEDULED_TASK_LINK));
    }

    /**
     * Synchronously checks whether the monitor is started or not.
     */
    private boolean isMonitorStarted() {
        try {
            runSynchronously(ch -> host.sendRequest(Operation
                    .createGet(host,
                            UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK,
                                    ElasticPlacementZoneMonitor.SCHEDULED_TASK_LINK))
                    .setReferer(host.getReferer())
                    .setCompletion(ch)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ensures a single synchronous execution of the monitoring task.
     */
    private void runMonitorOnce() {
        stopMonitor();
        startMonitor();
        stopMonitor();
    }

    /**
     * Runs the given operation synchronously by providing appropriate completion handler.
     */
    private void runSynchronously(Consumer<CompletionHandler> operationToRun) {
        TestContext ctx = testCreate(1);
        operationToRun.accept((o, e) -> {
            if (e != null) {
                ctx.failIteration(e);
            } else {
                ctx.completeIteration();
            }
        });
        ctx.await();
    }
}
