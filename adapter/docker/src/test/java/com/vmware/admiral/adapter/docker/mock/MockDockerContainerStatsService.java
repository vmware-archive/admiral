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

package com.vmware.admiral.adapter.docker.mock;

import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Mock for servicing requests for container stats
 */
public class MockDockerContainerStatsService extends StatelessService {

    @Override
    public void handleGet(Operation get) {
        @SuppressWarnings("unchecked")
        Map<String, String> props = get.getBody(Map.class);
        if (Boolean.valueOf(props.get("fail"))) {
            get.fail(404);
        } else {
            get.setBody("{\"read\":\"2015-06-25T21:19:13.647024404Z\",\"networks\":{\"eth0\":{\"rx_bytes\":0,\"rx_packets\":0,\"rx_errors\":0,\"rx_dropped\":0,\"tx_bytes\":0,\"tx_packets\":0,\"tx_errors\":0,\"tx_dropped\":0}},\"precpu_stats\":{\"cpu_usage\":{\"total_usage\":225018496308,\"percpu_usage\":[119697189919,105321306389],\"usage_in_kernelmode\":16350000000,\"usage_in_usermode\":179170000000},\"system_cpu_usage\":4346470000000,\"throttling_data\":{\"periods\":0,\"throttled_periods\":0,\"throttled_time\":0}},\"cpu_stats\":{\"cpu_usage\":{\"total_usage\":225019945078,\"percpu_usage\":[119698294886,105321650192],\"usage_in_kernelmode\":16350000000,\"usage_in_usermode\":179170000000},\"system_cpu_usage\":4348490000000,\"throttling_data\":{\"periods\":0,\"throttled_periods\":0,\"throttled_time\":0}},\"memory_stats\":{\"usage\":1494908928,\"max_usage\":2316062720,\"stats\":{\"active_anon\":850407424,\"active_file\":497885184,\"cache\":644501504,\"hierarchical_memory_limit\":9223372036854771712,\"hierarchical_memsw_limit\":9223372036854771712,\"inactive_anon\":0,\"inactive_file\":146616320,\"mapped_file\":25489408,\"pgfault\":670079,\"pgmajfault\":1393,\"pgpgin\":793615,\"pgpgout\":622316,\"rss\":850407424,\"rss_huge\":790626304,\"swap\":0,\"total_active_anon\":850407424,\"total_active_file\":497885184,\"total_cache\":644501504,\"total_inactive_anon\":0,\"total_inactive_file\":146616320,\"total_mapped_file\":25489408,\"total_pgfault\":670079,\"total_pgmajfault\":1393,\"total_pgpgin\":793615,\"total_pgpgout\":622316,\"total_rss\":850407424,\"total_rss_huge\":790626304,\"total_swap\":0,\"total_unevictable\":0,\"total_writeback\":0,\"unevictable\":0,\"writeback\":0},\"failcnt\":0,\"limit\":16833019904},\"blkio_stats\":{\"io_service_bytes_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":225280},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":225280},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":225280},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":236748800},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":236748800},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":236748800}],\"io_serviced_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":4},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":4},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":4},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":20054},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":20054},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":20054}],\"io_queue_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":0}],\"io_service_time_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":21868207},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":21868207},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":21868207},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":77488967912},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":77488967912},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":77488967912}],\"io_wait_time_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":61199},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":61199},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":61199},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":10362149210},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":10362149210},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":10362149210}],\"io_merged_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"Read\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Async\",\"value\":0},{\"major\":8,\"minor\":0,\"op\":\"Total\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Read\",\"value\":1762},{\"major\":8,\"minor\":16,\"op\":\"Write\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Sync\",\"value\":0},{\"major\":8,\"minor\":16,\"op\":\"Async\",\"value\":1762},{\"major\":8,\"minor\":16,\"op\":\"Total\",\"value\":1762}],\"io_time_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"\",\"value\":39},{\"major\":8,\"minor\":16,\"op\":\"\",\"value\":48551}],\"sectors_recursive\":[{\"major\":8,\"minor\":0,\"op\":\"\",\"value\":440},{\"major\":8,\"minor\":16,\"op\":\"\",\"value\":462400}]}}");
            super.handleGet(get);
        }
    }
}
