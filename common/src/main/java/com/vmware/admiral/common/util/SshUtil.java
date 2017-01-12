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

package com.vmware.admiral.common.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;

import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class SshUtil {

    public static Result exec(String hostname, AuthCredentialsServiceState credentials,
            String command) {
        return exec(hostname, credentials, command, 0);
    }

    public static Result exec(String hostname, AuthCredentialsServiceState credentials,
            String command,
            int timeout) {
        return asyncExec(hostname, credentials, command).join(timeout);
    }

    /**
     * This creates an asynch execution, returning the client and session for management of the user
     * Client creation itself is slow, use {@link SshUtil#asyncExec(SSHClient, String)} for performance
     *
     * @param hostname
     * @param credentials
     * @param command
     * @param timeout
     * @return
     */
    public static AsyncResult asyncExec(String hostname, AuthCredentialsServiceState credentials,
            String command) {
        AsyncResult result = new AsyncResult();
        try {
            SSHClient client = getDefaultSshClient(hostname, credentials);
            result.client = client;
            try {
                Session session = client.startSession();
                result.session = session;
                try {
                    result.command = session.exec(command);
                } catch (Throwable e) {
                    result.error = e;
                    session.close();
                    client.disconnect();
                }
            } catch (Throwable e) {
                result.error = e;
                client.disconnect();
            }
        } catch (Throwable e) {
            result.error = e;
        }

        return result;
    }

    /**
     * This creates an asynch execution, returning the client and session for management of the user
     * It will use the client provided and not close it, so it can be reused
     *
     * @param client
     * @param command
     * @param timeout
     * @return
     */
    public static AsyncResult asyncExec(SSHClient client, String command) {
        AsyncResult result = new AsyncResult();
        try {
            result.client = client;
            try {
                Session session = client.startSession();
                result.session = session;
                try {
                    result.command = session.exec(command);
                } catch (Throwable e) {
                    result.error = e;
                    session.close();
                }
            } catch (Throwable e) {
                result.error = e;
            }
        } catch (Throwable e) {
            result.error = e;
        }

        return result;
    }

    public static Throwable download(String hostname, AuthCredentialsServiceState credentials,
            String localPath, String remotePath) {
        return new SftpOperation() {
            @Override
            public void doOperation(SFTPClient client) throws IOException {
                client.get(remotePath, localPath);
            }
        }.execute(hostname, credentials);
    }

    public static Throwable upload(String hostname, AuthCredentialsServiceState credentials,
            String localPath, String remotePath) {
        return new SftpOperation() {
            @Override
            public void doOperation(SFTPClient client) throws IOException {
                client.put(localPath, remotePath);
            }
        }.execute(hostname, credentials);
    }

    public static Throwable upload(String hostname, AuthCredentialsServiceState credentials,
            InputStream stream, String remotePath) {
        File tmp;
        try {
            tmp = File.createTempFile("scp-temp", ".tmp");
        } catch (IOException e) {
            return e;
        }
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            int b = stream.read();
            while (b != -1) {
                fos.write(b);
                b = stream.read();
            }
        } catch (Throwable t) {
            return t;
        }
        return upload(hostname, credentials, tmp.getAbsolutePath(), remotePath);
    }

    private abstract static class SftpOperation {

        public abstract void doOperation(SFTPClient client) throws IOException;

        public Throwable execute(String hostname, AuthCredentialsServiceState credentials) {
            try (SSHClient client = getDefaultSshClient(hostname, credentials)) {
                try (SFTPClient sftp = client.newSFTPClient()) {
                    doOperation(sftp);
                }
            } catch (Throwable e) {
                return e;
            }

            return null;
        }
    }

    public static Future<Throwable> asyncDownload(String hostname,
            AuthCredentialsServiceState credentials,
            String localPath, String remotePath) {
        ExecutorService es = Executors.newFixedThreadPool(1);
        return es.submit(() -> download(hostname, credentials, localPath, remotePath));
    }

    public static Future<Throwable> asyncUpload(String hostname,
            AuthCredentialsServiceState credentials,
            String localPath, String remotePath) {
        ExecutorService es = Executors.newFixedThreadPool(1);
        return es.submit(() -> upload(hostname, credentials, localPath, remotePath));
    }

    public static Future<Throwable> asyncUpload(String hostname,
            AuthCredentialsServiceState credentials,
            InputStream stream, String remotePath) {
        ExecutorService es = Executors.newFixedThreadPool(1);
        return es.submit(() -> upload(hostname, credentials, stream, remotePath));
    }

    public static SSHClient getDefaultSshClient(String hostname,
            AuthCredentialsServiceState credentials) throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new InsecureHostkeyVerifier());
        client.connect(hostname);
        String privateKey = EncryptionUtils.decrypt(credentials.privateKey);
        if (credentials.type != null && credentials.type.equals("PublicKey")) {
            OpenSSHKeyFile openSSHKeyFile = new OpenSSHKeyFile();
            openSSHKeyFile.init(privateKey, null);
            client.authPublickey(credentials.userEmail, openSSHKeyFile);
        } else {
            client.authPassword(credentials.userEmail, privateKey);
        }

        return client;
    }

    public static class AsyncResult implements Closeable {
        public SSHClient client;
        public Session session;
        public Command command;
        public Throwable error;

        public Result join() {
            return join(0);
        }

        public Result join(int timeout) {
            Result result = new Result();
            try {
                if (error != null) {
                    result.error = error;
                } else {
                    if (timeout > 0) {
                        command.join(timeout, TimeUnit.SECONDS);
                    } else {
                        command.join();
                    }

                    result.exitCode = command.getExitStatus();
                    result.out = command.getInputStream();
                    result.err = command.getErrorStream();
                }
            } catch (Throwable e) {
                result.error = e;
            }

            return result;
        }

        public boolean isDone() {
            if (error != null) {
                return true;
            }

            return command.getExitStatus() != null && !command.isOpen();
        }

        public Result toResult() {
            Result result = new Result();
            if (!isDone()) {
                result.error = new LocalizableValidationException("The command has not terminated yet!", "common.ssh.command.not.finished");
            } else {
                if (this.error != null) {
                    result.error = this.error;
                } else {
                    result.exitCode = command.getExitStatus();
                    result.out = command.getInputStream();
                    result.err = command.getErrorStream();
                }
            }

            return result;
        }

        @Override
        public void close() {
            try {
                if (session != null) {
                    session.close();
                }
            } catch (TransportException | ConnectionException e) {
                error = e;
            }
            try {
                if (client != null) {
                    client.disconnect();
                }
            } catch (IOException e) {
                error = e;
            }
        }
    }

    public static class Result {
        public int exitCode = -1;
        public InputStream out;
        public InputStream err;
        public Throwable error;

        public ConsumedResult consume() throws IOException {
            ConsumedResult c = new ConsumedResult();
            c.exitCode = exitCode;
            c.out = readStream(out);
            c.err = readStream(err);
            c.error = error;

            return c;
        }
    }

    public static class ConsumedResult {
        public int exitCode = -1;
        public String out;
        public String err;
        public Throwable error;

        private ConsumedResult() {
            // this is a by-product from result, people should not be creating any of this
        }
    }

    public static class InsecureHostkeyVerifier implements HostKeyVerifier {

        @Override
        public boolean verify(String hostname, int port, PublicKey key) {
            return true;
        }

    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }

        StringWriter sw = new StringWriter();

        int b = is.read();
        while (b != -1) {
            sw.write(b);
            b = is.read();
        }
        return sw.toString();
    }
}
