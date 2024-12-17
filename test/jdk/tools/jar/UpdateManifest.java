/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.spi.ToolProvider;
import java.util.zip.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 6434207 6442687 6984046
 * @summary Ensure that jar ufm actually updates the
 *          existing jar file's manifest with contents of the
 *          manifest file.
 * @modules jdk.jartool
 * @comment we use othervm because the test updates the logging
 *          level of java.util.jar logger
 * @run junit/othervm UpdateManifest
 */
public class UpdateManifest {
    private static final Path SCRATCH_DIR = Path.of(".");
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private static final Logger JAR_LOGGER = Logger.getLogger("java.util.jar");

    @BeforeAll
    static void disableJarLogger() {
        // Attributes.read() can log a message we don't care to see in the test logs.
        JAR_LOGGER.setLevel(Level.OFF);
    }

    /*
     * "jar cfe" command is used to create a JAR file with a specific Main-Class.
     * Then "jar ufe" is run against that JAR to update the Main-Class. The test
     * verifies that the Main-Class in the existing manifest is updated to the
     * newer value.
     */
    @Test
    public void testManifestExistence() throws Throwable {
        // Create a file to put in a jar file
        Path existence = Files.createFile(Path.of("existence"));
        // Create a jar file, specifying a Main-Class
        final Path jarFile = Path.of("um-existence.jar");
        Files.deleteIfExists(jarFile);  // remove (any) pre-existing JAR file first!
        int status = JAR_TOOL.run(System.out, System.err, "cfe", jarFile.toString(),
                                  "Hello", existence.toString());
        assertEquals(0, status, "unexpected exit code of jar cfe command");
        checkMainClass(jarFile, "Hello");

        // Update that jar file by changing the Main-Class
        status = JAR_TOOL.run(System.out, System.err, "ufe", jarFile.toString(), "Bye");
        assertEquals(0, status, "unexpected exit code of jar ufe command");
        checkMainClass(jarFile, "Bye");
    }

    /*
     * "jar cfm" command is used to create a JAR file with a specific manifest file.
     * Then "jar ufm" is run against that JAR with a different manifest file. The test
     * verifies that the updated manifest file in the JAR will contain a manifest file
     * which has its attributes merged from the first and the second manifest file.
     */
    @Test
    public void testManifestContents() throws Throwable {
        // Some attributes that we expect to find in the updated manifest
        final String animal =
            "Name: animal/marsupial";
        final String specTitle =
            "Specification-Title: Wombat";

        // Create a text file with manifest entries
        final Path manifestOrig = Files.createTempFile(SCRATCH_DIR, "manifestOrig", ".txt");
        try (PrintWriter pw = new PrintWriter(manifestOrig.toFile())) {
            pw.println("Manifest-Version: 1.0");
            pw.println("Created-By: 1.7.0-internal (Oracle Corporation)");
            pw.println("");
            pw.println(animal);
            pw.println(specTitle);
        }
        // an arbitrary file that will be added to the JAR file
        final Path hello = Files.createFile(Path.of("hello"));
        // Create a JAR file
        final Path jarFile = Path.of("um-test.jar");
        Files.deleteIfExists(jarFile); // remove (any) pre-existing JAR file first!
        int status = JAR_TOOL.run(System.out, System.err, "cfm", jarFile.toString(),
                                  manifestOrig.toString(), hello.toString());
        assertEquals(0, status, "unexpected exit code of jar cfm command");

        final String createdBy =
                "Created-By: 1.7.0-special (Oracle Corporation)";
        final String specVersion =
                "Specification-Version: 1.0.0.0";
        // Create a new manifest, to use in updating the jar file.
        final Path manifestUpdate = Files.createTempFile(SCRATCH_DIR, "manifestUpdate", ".txt");
        try (PrintWriter pw = new PrintWriter(manifestUpdate.toFile())) {
            pw.println(createdBy); // replaces line in the original
            pw.println("");
            pw.println(animal);
            pw.println(specVersion); // addition to animal/marsupial section
        }

        // Update jar file with manifest
        status = JAR_TOOL.run(System.out, System.err, "ufm",
                              jarFile.toString(), manifestUpdate.toString());
        assertEquals(0, status, "unexpected exit code of jar ufm command");

        // Extract jar, and verify contents of manifest file
        try (ZipFile zf = new ZipFile(jarFile.toFile())) {

            ZipEntry ze = zf.getEntry("META-INF/MANIFEST.MF");
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(ze)))) {
                r.readLine(); // skip Manifest-Version
                assertEquals(createdBy, r.readLine(), "unexpected attribute in manifest");
                r.readLine(); // skip blank line
                assertEquals(animal, r.readLine(), "unexpected attribute in manifest");
                final String s = r.readLine();
                if (s.equals(specVersion)) {
                    assertEquals(specTitle, r.readLine(), "unexpected attribute in manifest");
                } else if (s.equals(specTitle)) {
                    assertEquals(specVersion, r.readLine(), "unexpected attribute in manifest");
                } else {
                    fail("Line in manifest: " + s + " did not match specVersion nor specTitle");
                }
            }
        }
    }

    private static void checkMainClass(final Path jarFile, final String mainClass)
            throws Throwable {
        try (JarFile jf = new JarFile(jarFile.toFile())) {
            final Manifest manifest = jf.getManifest();
            assertNotNull(manifest, "Missing manifest file in " + jarFile);
            final String actual = manifest.getMainAttributes().getValue("Main-Class");
            assertEquals(mainClass, actual, "unexpected Main-Class in manifest");
        }
    }
}
