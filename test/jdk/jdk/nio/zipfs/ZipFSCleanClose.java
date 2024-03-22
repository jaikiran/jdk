/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8291712
 * @summary verifies that a ZipFileSystem created using ZipFileSystemProvider.newFileSystem()
 *          is cleanly unregistered when ZipFileSystem is closed
 * @modules jdk.zipfs
 * @run junit ZipFSCleanClose
 */
public class ZipFSCleanClose {

    private static final String ZIPFS_SCHEME = "jar";
    private static final String ENTRY_NAME = "foo-bar";
    private static final String ENTRY_DATA = "Tennis Anyone";

    /**
     * Creates a Zip file
     *
     * @param zip path for Zip to be created
     */
    private static void createZip(Path zip) throws IOException {
        try (var os = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            var ze = new ZipEntry(ENTRY_NAME);
            byte[] data = ENTRY_DATA.getBytes(US_ASCII);
            var crc = new CRC32();
            ze.setMethod(ZipEntry.STORED);
            crc.update(data);
            ze.setCrc(crc.getValue());
            ze.setSize(data.length);
            zos.putNextEntry(ze);
            zos.write(data);
        }
    }

    /**
     * Creates a {@code ZipFileSystem} over a ZIP file residing on the underlying filesystem,
     * by calling {@link jdk.nio.zipfs.ZipFileSystemProvider#newFileSystem(Path, Map)}.
     * The test then deletes the ZIP file from the underlying filesystem and then closes
     * the {@code ZipFileSystem} instance. The close() is expected to complete without any
     * exception.
     * Once closed, the test then recreates the ZIP file on the underlying filesystem and then
     * constructs a new {@code ZipFileSystem} for it, using the same
     * {@link jdk.nio.zipfs.ZipFileSystemProvider#newFileSystem(Path, Map)} method. This call to
     * {@code newFileSystem} is expected to succeed.
     */
    @Test
    public void testPathNewFileSystem() throws Exception {
        Path tmpFile = Files.createTempFile(Path.of("."), "8291712", ".zip");
        createZip(tmpFile);
        try (FileSystem fs = FileSystems.newFileSystem(tmpFile)) {
            assertEquals(ZIPFS_SCHEME, fs.provider().getScheme(), "unexpected filesystem provider");
            System.out.println("created zip filesystem " + fs + " for zip file " + tmpFile);
            verifyEntry(fs);
            // now delete the underlying file
            Files.delete(tmpFile);
            System.out.println("Deleted " + tmpFile + ", now closing zipfs " + fs);
        }
        // recreate the ZIP file and then a new instance of ZipFileSystem for it
        System.out.println("recreating zip file " + tmpFile);
        createZip(tmpFile);
        try (FileSystem fs = FileSystems.newFileSystem(tmpFile)) {
            assertEquals(ZIPFS_SCHEME, fs.provider().getScheme(), "unexpected filesystem provider");
            System.out.println("recreated zip filesystem " + fs + " for zip file " + tmpFile);
            verifyEntry(fs);
        }
    }

    /**
     * Creates a {@code ZipFileSystem} over a ZIP file residing on the underlying filesystem,
     * by calling {@link jdk.nio.zipfs.ZipFileSystemProvider#newFileSystem(URI, Map)}.
     * The test then deletes the ZIP file from the underlying filesystem and then closes
     * the {@code ZipFileSystem} instance. The close() is expected to complete without any
     * exception.
     * Once closed, the test then recreates the ZIP file on the underlying filesystem and then
     * constructs a new {@code ZipFileSystem} for it, using the same
     * {@link jdk.nio.zipfs.ZipFileSystemProvider#newFileSystem(URI, Map)} method. This call to
     * {@code newFileSystem} is expected to succeed.
     */
    @Test
    public void testURINewFileSystem() throws Exception {
        Path tmpFile = Files.createTempFile(Path.of("."), "8291712", ".zip");
        createZip(tmpFile);
        URI fileURI = new URI(ZIPFS_SCHEME + ":" + tmpFile.toUri() + "!/");
        System.out.println("uri is " + fileURI);
        try (FileSystem fs = FileSystems.newFileSystem(fileURI, Map.of())) {
            assertEquals(ZIPFS_SCHEME, fs.provider().getScheme(), "unexpected filesystem provider");
            System.out.println("created zip filesystem " + fs + " for zip file " + tmpFile);
            verifyEntry(fs);
            // now delete the underlying file
            Files.delete(tmpFile);
            System.out.println("Deleted " + tmpFile + ", now closing zipfs " + fs);
        }
        // recreate the ZIP file and then a new instance of ZipFileSystem for it
        System.out.println("recreating zip file " + tmpFile);
        createZip(tmpFile);
        try (FileSystem fs = FileSystems.newFileSystem(fileURI, Map.of())) {
            assertEquals(ZIPFS_SCHEME, fs.provider().getScheme(), "unexpected filesystem provider");
            System.out.println("recreated zip filesystem " + fs + " for zip file " + tmpFile);
            verifyEntry(fs);
        }
    }

    // verify that the filesystem has the expected Path and the contents of the file
    // at that Path are as expected
    private static void verifyEntry(FileSystem fs) throws IOException {
        Path entryPath = fs.getPath(ENTRY_NAME);
        String content = Files.readString(entryPath, US_ASCII);
        assertEquals(ENTRY_DATA, content, "unexpected content in entry "
                + ENTRY_NAME + " of filesystem " + fs);
    }
}
