/*
 * The MIT License
 *
 * Copyright 2023 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.monitor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import jenkins.model.Jenkins;
import org.junit.Test;

public class EndOfLifeDebian10AdminMonitorTest {

    private final EndOfLifeDebian10AdminMonitor monitor = new EndOfLifeDebian10AdminMonitor();

    public EndOfLifeDebian10AdminMonitorTest() {
    }

    @Test
    public void testGetIdentifier() {
        assertThat(monitor.getIdentifier(), is("debian_10"));
    }

    @Test
    public void testGetDependencyName() {
        assertThat(monitor.getDependencyName(), is("Debian 10"));
    }

    @Test
    public void testGetDisplayName() {
        assertThat(monitor.getDisplayName(), is("Debian 10 end of life"));
    }

    @Test
    public void testGetBeginDisplayDate() {
        assertThat(monitor.getBeginDisplayDate(), is("2024-01-01"));
    }

    @Test
    public void testGetDocumentationURL() {
        assertThat(monitor.getDocumentationURL(), is("https://www.jenkins.io/redirect/dependency-end-of-life"));
    }

    @Test
    public void testIsUnsupported() {
        assertThat(monitor.isUnsupported(), is(LocalDate.now().isAfter(LocalDate.of(2024, 6, 30))));
    }

    @Test
    public void testGetRequiredPermission() {
        assertThat(monitor.getRequiredPermission(), is(Jenkins.SYSTEM_READ));
    }

}