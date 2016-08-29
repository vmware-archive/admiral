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

package com.vmware.admiral.adapter.docker.util;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;

public class DockerStreamUtil {

    private static final int HEADER_SIZE = 8;
    private static final int FRAME_SIZE_OFFSET = 4;

    /**
     * Decodes a byte array as fetched by xenon client and from {@link com.vmware.xenon.common.Operation#getBodyRaw()}.
     * Implemented by the algorithms Docker described in
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.21/#attach-to-a-container.
     * Applicable for requests attaching to a container, executing a command and reading stream.
     *
     * @param body
     * @return
     * @throws EOFException
     */
    public static String decodeFullRawResponce(byte[] body) throws EOFException {
        StringBuilder sb = new StringBuilder();

        ByteArrayInputStream is = new ByteArrayInputStream(body);

        while (is.available() > 0) {
            final byte[] headerBytes = new byte[HEADER_SIZE];
            int n = is.read(headerBytes, 0, HEADER_SIZE);

            if (n != HEADER_SIZE) {
                throw new EOFException("Cannot read header of size " + HEADER_SIZE);
            }

            final ByteBuffer header = ByteBuffer.wrap(headerBytes);
            header.position(FRAME_SIZE_OFFSET);
            final int frameSize = header.getInt();

            // Read frame
            final byte[] frame = new byte[frameSize];
            n = is.read(frame, 0, frameSize);

            if (n != frameSize) {
                throw new EOFException("Cannot read frame of size " + frameSize);
            }

            sb.append(new String(frame));
        }

        return sb.toString();
    }
}
