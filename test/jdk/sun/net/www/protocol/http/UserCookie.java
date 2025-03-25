/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6439651
 * @summary Sending "Cookie" header with JRE 1.5.0_07 doesn't work anymore
 * @modules jdk.httpserver
 * @library /test/lib
 * @run main/othervm UserCookie
 */

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

import static java.net.Proxy.NO_PROXY;

public class UserCookie {
    public static void main(String[] args) throws Exception {
        final HttpServer server = startHttpServer();
        try {
            runTest(server);
        } finally {
            server.stop(0);
        }
    }

    private static void runTest(final HttpServer httpServer) throws Exception {
        // set default CookieHandler to accept only accepts cookies from original server.
        CookieHandler.setDefault(new CookieManager());

        final InetSocketAddress address = httpServer.getAddress();
        final URI uri = URIBuilder.newBuilder()
                .scheme("http")
                .host(address.getHostName())
                .port(address.getPort())
                .path("/test/")
                .build();
        System.out.println("issuing request to " + uri);
        final HttpURLConnection uc = (HttpURLConnection) uri.toURL().openConnection(NO_PROXY);
        uc.setRequestProperty("Cookie", "value=ValueDoesNotMatter");

        final int resp = uc.getResponseCode();
        System.out.println("Response Code is " + resp);
        if (resp != 200) {
            throw new RuntimeException("Failed: Cookie header was not retained, status code: "
                    + resp);
        }
    }

    /**
     * Start and return a HttpServer
     */
    private static HttpServer startHttpServer() throws IOException {
        final InetAddress address = InetAddress.getLocalHost();
        if (!InetAddress.getByName(address.getHostName()).equals(address)) {
            // if this happens then we should possibly change the client
            // side to use the address literal in its URL instead of
            // the host name.
            throw new IOException(address.getHostName()
                                  + " resolves to "
                                  + InetAddress.getByName(address.getHostName())
                                  + " not to "
                                  + address + ": check host configuration.");
        }
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress(address, 0), 0);
        try {
            // create HttpServer context
            httpServer.createContext("/test/", new MyHandler());
            httpServer.start();
        } catch (Exception e) {
            httpServer.stop(0);
            throw e;
        }
        return httpServer;
    }

    private static class MyHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            final Headers reqHeaders = t.getRequestHeaders();
            final List<String> cookie = reqHeaders.get("Cookie");
            final int statusCode;
            if (cookie == null) {
                // missing cookie
                System.out.println("missing cookie in request " + t.getRequestURI());
                statusCode = 400;
            } else {
                final String cookieVal = cookie.get(0);
                if (!cookieVal.equals("value=ValueDoesNotMatter")) {
                    // unexpected value
                    System.out.println("unexpected cookie: \"" + cookieVal + "\" in request "
                            + t.getRequestURI());
                    statusCode = 400;
                } else {
                    // expected cookie val
                    statusCode = 200;
                }
            }
            t.sendResponseHeaders(statusCode, -1);
            t.close();
        }
    }
}
