/*
 * The MIT License
 *
 * Copyright 2015 Christopher Simons
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
package hudson.jobs;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

import com.gargoylesoftware.htmlunit.Page;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.listeners.ItemListener;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;

/**
 * Tests the /createItem REST API.
 *
 * @author Christopher Simons
 */
public class CreateItemTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Before
    public void setup() {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
    }

    @Issue("JENKINS-31235")
    @Test
    public void testCreateItemFromCopy() throws Exception {
        rule.jenkins.setCrumbIssuer(null);

        String sourceJobName = "sourceJob";
        rule.createFreeStyleProject(sourceJobName);

        String newJobName = "newJob";
        URL apiURL = new URL(MessageFormat.format(
                    "{0}createItem?mode=copy&from={1}&name={2}",
                    rule.getURL().toString(), sourceJobName, newJobName));

        WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
        deleteContentTypeHeader(request);

        Page p = rule.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(request);
        assertEquals("Creating job from copy should succeed.",
                HttpURLConnection.HTTP_OK,
                p.getWebResponse().getStatusCode());
    }

    @Issue("JENKINS-34691")
    @Test
    public void vetoCreateItemFromCopy() throws Exception {
        rule.jenkins.setCrumbIssuer(null);

        String sourceJobName = "sourceJob";
        rule.createFreeStyleProject(sourceJobName);

        String newJobName = "newJob";
        URL apiURL = new URL(MessageFormat.format(
                    "{0}createItem?mode=copy&from={1}&name={2}",
                    rule.getURL().toString(), sourceJobName, newJobName));

        WebRequest request = new WebRequest(apiURL, HttpMethod.POST);
        deleteContentTypeHeader(request);

        Page p = rule.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .getPage(request);

        assertEquals("Creating job from copy should fail.", 
                HttpURLConnection.HTTP_BAD_REQUEST, 
                p.getWebResponse().getStatusCode());
        assertThat(rule.jenkins.getItem("newJob"), nullValue());
    }

    private void deleteContentTypeHeader(WebRequest request) {
        request.setEncodingType(null);
    }

    @Test
    public void createWithFolderPaths() throws Exception {
        rule.jenkins.setCrumbIssuer(null);
        rule.createFolder("d1").createProject(FreeStyleProject.class, "p");
        MockFolder d2 = rule.createFolder("d2");

        JenkinsRule.WebClient wc = rule.createWebClient();

        wc.getPage(new WebRequest(new URL(d2.getAbsoluteUrl() + "createItem?mode=copy&name=p2&from=../d1/p"), HttpMethod.POST));
        assertNotNull(d2.getItem("p2"));

        wc.getPage(new WebRequest(new URL(d2.getAbsoluteUrl() + "createItem?mode=copy&name=p3&from=/d1/p"), HttpMethod.POST));
        assertNotNull(d2.getItem("p3"));
    }

    @TestExtension("vetoCreateItemFromCopy")
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onCheckCopy(Item src, ItemGroup parent) throws Failure {
            if ("sourceJob".equals(src.getName())) {
                throw new Failure("Go away I don't like you");
            }
        }
    }

}
