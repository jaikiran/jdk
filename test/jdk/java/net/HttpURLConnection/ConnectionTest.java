/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @summary  test
 * @run main/othervm -Djava.util.logging.config.file=${test.src}/connection-test-logging.properties ConnectionTest
 */

public class ConnectionTest {

    private volatile int SERVER_PORT;
    private volatile boolean isServerReady = false;
    private CountDownLatch coutCountDownLatch = new CountDownLatch(1);

    private void init() throws Exception {
        serverInit();
        clientInit();
    }

    private void serverInit() {
        new Thread(() -> {
            try {
                createServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) throws Exception {
        ConnectionTest simpleTest = new ConnectionTest();
        simpleTest.init();
        simpleTest.coutCountDownLatch.await();
        System.out.println("Test completed");
    }

    private void clientInit() throws Exception {
        try {
            System.out.println("Following are Existing System Properties if set any");
            System.setProperty("java.net.useSystemProxies", "false");
            System.out.println("http.proxyPort:" + System.getProperty("http.proxyPort"));
            System.out.println("http.proxyHost:" + System.getProperty("http.proxyHost"));
            while (!isServerReady) {
                System.out.println("Client waiting for server to be ready");
                Thread.sleep(2000);
            }
            URL url = URIBuilder.newBuilder().scheme("http").loopback().port(SERVER_PORT).toURL();
            System.out.println("Connecting to Server:" + url);
            HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            System.out.println("Got URLConnection");
            InputStreamReader inputStreamReader = new InputStreamReader(httpUrlConnection.getInputStream());
            System.out.println("Got InputStream");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            while (true) {
                System.out.println("Reading line from server");
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    break;
                }
                System.out.println(readLine);
            }
        } finally {
            this.coutCountDownLatch.countDown();
        }
    }

    private void createServer() throws IOException {

        ServerSocket serverSocket = null;
        OutputStreamWriter out = null;
        Socket socket = null;
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            System.out.println("loopback=" + loopback);
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            SERVER_PORT = serverSocket.getLocalPort();
            isServerReady = true;
            System.out.println("ServerPortNumber:" + SERVER_PORT);
            System.out.println("SERVER:ServerReady:" + isServerReady);
            socket = serverSocket.accept();
            System.out.println("Server accepted connection from " + socket);
            out = new OutputStreamWriter(socket.getOutputStream());
            String BODY = "SERVER REPLY: Hello world";
            String CLEN = "Content-Length: " + BODY.length() + "\r\n";

            /* send the header */
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Content-Type: text/plain; charset=iso-8859-1\r\n");

            out.write(CLEN);
            out.write("\r\n");
            out.write(BODY);
            out.flush();
            System.out.println("Server wrote response");
        } catch (Throwable t) {
            System.err.println("Server received exception: " + t);
            t.printStackTrace();
        } finally {
            System.out.println("Server exiting");
            if (serverSocket != null) {
                serverSocket.close();
                System.out.println("Server closed server socket " + serverSocket);
            }

            if (out != null) {
                out.close();
                System.out.println("Server closed outputstream");
            }

            if (socket != null) {
                socket.close();
                System.out.println("Server closed client socket " + socket);
            }
        }
    }
}

class URIBuilder {

    public static URIBuilder newBuilder() {
        return new URIBuilder();
    }

    private String scheme;
    private String userInfo;
    private String host;
    private int port;
    private String path;
    private String query;
    private String fragment;

    private URIBuilder() {
    }

    public URIBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    public URIBuilder host(String host) {
        this.host = host;
        return this;
    }

    public URIBuilder host(InetAddress address) {
        String hostaddr = address.isAnyLocalAddress()
                ? "localhost" : address.getHostAddress();
        return host(hostaddr);
    }

    public URIBuilder loopback() {
        return host(InetAddress.getLoopbackAddress().getHostAddress());
    }

    public URIBuilder port(int port) {
        this.port = port;
        return this;
    }

    public URIBuilder path(String path) {
        this.path = path;
        return this;
    }

    public URIBuilder query(String query) {
        this.query = query;
        return this;
    }

    public URIBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    public URI build() throws URISyntaxException {
        return new URI(scheme, userInfo, host, port, path, query, fragment);
    }

    public URI buildUnchecked() {
        try {
            return build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public URL toURL() throws URISyntaxException, MalformedURLException {
        return build().toURL();
    }

    public URL toURLUnchecked() {
        try {
            return toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}




