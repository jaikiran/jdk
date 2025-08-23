/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8364755
 * @summary verify that operations on DeflaterOutputStream throw the specified
 *          exception when the DeflaterOutputStream is closed
 * @run junit DeflaterOutputStreamCloseTest
 */
class DeflaterOutputStreamCloseTest {

    @Test
    void testAfterClose() throws Exception {
        final OutputStream os = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                throw new IOException("intentionally thrown from close()");
            }
        };

        final DeflaterOutputStream dos = new DeflaterOutputStream(os);
        assertThrows(IOException.class, dos::close);

        assertThrows(IOException.class, () -> dos.write(0x01));
        assertThrows(IOException.class, () -> dos.write(new byte[]{0x01, 0x02}));
        assertThrows(IOException.class, () -> dos.write(new byte[]{0x01, 0x02}, 1, 1));
        assertThrows(IOException.class, dos::finish);
        assertThrows(IOException.class, dos::flush);

        // close() should not throw when already closed
        dos.close();
    }
}
