/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6558853 8371509
 * @summary  getHostAddress() on Socket connection using IPv6 link-local address should
 *           have zone id.
 *           This test needs to bind to the wildcard address and as such is susceptible to
 *           fail intermittently because of port reuse issues.
 * @key intermittent
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        jdk.test.lib.Platform
 *        jtreg.SkippedException
 * @run main ${test.main.class}
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.NetworkConfiguration;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class B6558853 {

    public static void main(String[] args) throws Throwable {
        final Optional<Inet6Address> linkLocalAddr = NetworkConfiguration.probe()
                .ip6Addresses()
                .filter(a -> a.isLinkLocalAddress())
                .findFirst();

        if (linkLocalAddr.isEmpty()) {
            throw new jtreg.SkippedException("Skipping test - no link-local IPv6 address found");
        }

        final Inet6Address ipv6Addr = linkLocalAddr.get();
        System.out.println("Using link-local address: " + ipv6Addr);
        // start the server
        final Server server = new Server();
        final Thread serverThread = Thread.ofPlatform().start(server);
        System.out.println("waiting for the server to be ready to accept connection");
        server.readyToAccept.await();

        // connect to the server
        final int serverPort = server.getAddress().getPort();
        final InetSocketAddress connectAddr = new InetSocketAddress(ipv6Addr, serverPort);
        System.out.println("attempting to connect to server at " + connectAddr);
        try (final Socket s = new Socket(connectAddr.getAddress(), connectAddr.getPort())) {
            final OutputStream out = s.getOutputStream();
            out.write(Server.EXPECTED_REQ_BYTES);
            s.shutdownOutput(); // we won't write anything more data from our side
            System.out.println("waiting for response from server");
            try (final InputStream in = s.getInputStream()) {
                final byte[] resp = in.readAllBytes();
                if (!Arrays.equals(Server.EXPECTED_RESP_BYTES, resp)) {
                    throw new Exception("unexpected response from server: " + Arrays.toString(resp));
                }
                System.out.println("received expected response from server");
            }
        }
        serverThread.join(); // wait for the server thread to complete

        // verify that there were no errors on the server and the getHostAddress() seen on the
        // server was the correct one
        final Throwable failure = server.failure;
        if (failure != null) {
            throw failure;
        }
    }

    private static final class Server implements Runnable {
        private static final byte[] EXPECTED_REQ_BYTES = "hello-6558853".getBytes(US_ASCII);
        private static final byte[] EXPECTED_RESP_BYTES = "done".getBytes(US_ASCII);

        private final CountDownLatch readyToAccept = new CountDownLatch(1);

        private volatile ServerSocket serverSocket;
        private volatile Throwable failure;

        private Server() {
        }

        private InetSocketAddress getAddress() {
            return (InetSocketAddress) serverSocket.getLocalSocketAddress();
        }

        @Override
        public void run() {
            // start the ServerSocket on a wildcard address
            try (var _ = serverSocket = new ServerSocket(0)) {
                System.out.println("server listening at " + serverSocket.getLocalSocketAddress());
                this.readyToAccept.countDown();
                while (true) {
                    try (final Socket peer = serverSocket.accept()) {
                        final byte[] reqBytes = peer.getInputStream().readAllBytes();
                        if (!Arrays.equals(EXPECTED_REQ_BYTES, reqBytes)) {
                            // unexpected connection, skip this and accept() any subsequent request
                            System.err.println("unexpected connection received from " + peer);
                            continue;
                        }
                        final InetAddress peerAddr = peer.getInetAddress();
                        final String peerHostAddr = peerAddr.getHostAddress();
                        System.out.println("server accepted connection from peer getHostAddress: "
                                + peerHostAddr);
                        if (!(peerAddr instanceof Inet6Address)) {
                            this.failure = new Exception("peer address isn't IPv6 address: " + peerAddr);
                        } else if (!peerHostAddr.contains("%")) {
                            this.failure = new Exception("unexpected address from getHostAddress: "
                                    + peerHostAddr);
                        }
                        // send a response to let the client know that
                        // the relevant checks are complete
                        try (final OutputStream out = peer.getOutputStream()) {
                            out.write(EXPECTED_RESP_BYTES);
                        }
                        return;
                    }
                }
            } catch (Throwable t) {
                if (this.failure != null) {
                    this.failure.addSuppressed(t);
                } else {
                    this.failure = t;
                }
                t.printStackTrace();
            }
        }
    }
}
