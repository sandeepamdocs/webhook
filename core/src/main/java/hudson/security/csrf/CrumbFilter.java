/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import hudson.util.MultipartFormDataParser;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kohsuke.stapler.HttpResponseRenderer;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponseWrapper;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Checks for and validates crumbs on requests that cause state changes, to
 * protect against cross site request forgeries.
 * 
 * @author dty
 */
public class CrumbFilter implements Filter {
    private static final String POST_PROCESSOR_EXCEPTION_CLASS_NAME = RequirePOST.Processor.class.getName() + "$1";

    /**
     * Because servlet containers generally don't specify the ordering of the initialization
     * (and different implementations indeed do this differently --- See HUDSON-3878),
     * we cannot use Hudson to the CrumbIssuer into CrumbFilter eagerly.
     */
    public CrumbIssuer getCrumbIssuer() {
        Jenkins h = Jenkins.getInstanceOrNull();
        if(h==null)     return null;    // before Jenkins is initialized?
        return h.getCrumbIssuer();
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        WebApp webApp = WebApp.get(filterConfig.getServletContext());
        addRequirePostCrumbHandler(webApp);
    }

    private void addRequirePostCrumbHandler(WebApp webApp) {
        webApp.getResponseRenderers().add(0, new HttpResponseRenderer() {
            @Override
            public boolean generateResponse(StaplerRequest req, StaplerResponse rsp, Object node, Object response) throws IOException, ServletException {
                if (response instanceof HttpResponseException) {
                    Class<?> c = response.getClass();
                    // the post processor exception is the 'try post'
                    // response, which needs to be modified to be crumb-aware
                    if (POST_PROCESSOR_EXCEPTION_CLASS_NAME.equals(c.getName())) {
                        CrumbIssuer crumbIssuer = getCrumbIssuer();
                        if (crumbIssuer != null) {
                            String crumbFieldName = crumbIssuer.getDescriptor().getCrumbRequestField();
                            StringWriter output = new StringWriter();
                            final PrintWriter resWriter = new PrintWriter(output);
                            StaplerResponse res = new StaplerResponseWrapper(rsp) {
                                public PrintWriter getWriter() throws IOException {
                                    return resWriter;
                                }
                                // Need extra methods since Jenkins upgraded to Servlet API 3
                                @Override
                                public void setContentLengthLong(long len) {
                                    getWrapped().setContentLengthLong(len);
                                }
                                @Override
                                public void setCharacterEncoding(String charset) {
                                    getWrapped().setCharacterEncoding(charset);
                                }
                                @Override
                                public String getContentType() {
                                    return getWrapped().getContentType();
                                }
                                @Override
                                public int getStatus() {
                                    return getWrapped().getStatus();
                                }
                                @Override
                                public Collection<String> getHeaders(String name) {
                                    return getWrapped().getHeaders(name);
                                }
                                @Override
                                public Collection<String> getHeaderNames() {
                                    return getWrapped().getHeaderNames();
                                }
                                @Override
                                public String getHeader(String name) {
                                    return getWrapped().getHeader(name);
                                }
                            };
                            ((HttpResponseException)response).generateResponse(req, res, node);
                            String crumbHiddenInput = "<input type=\"hidden\" name=\""+crumbFieldName+"\" value=\""+crumbIssuer.getCrumb(req)+"\"/>";
                            rsp.getWriter().write(output.toString().replace("</form>",crumbHiddenInput+"</form>"));
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        CrumbIssuer crumbIssuer = getCrumbIssuer();
        if (crumbIssuer == null || !(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equals(httpRequest.getMethod())) {
            for (CrumbExclusion e : CrumbExclusion.all()) {
                if (e.process(httpRequest,httpResponse,chain))
                    return;
            }

            String crumbFieldName = crumbIssuer.getDescriptor().getCrumbRequestField();
            String crumbSalt = crumbIssuer.getDescriptor().getCrumbSalt();

            boolean valid = false;
            String crumb = extractCrumbFromRequest(httpRequest, crumbFieldName);
            if (crumb == null) {
                // compatibility for clients that hard-code the default crumb name up to Jenkins 1.TODO
                extractCrumbFromRequest(httpRequest, ".crumb");
            }
            if (crumb != null) {
                if (crumbIssuer.validateCrumb(httpRequest, crumbSalt, crumb)) {
                    valid = true;
                } else {
                    LOGGER.log(Level.WARNING, "Found invalid crumb {0}.  Will check remaining parameters for a valid one...", crumb);
                }
            }

            if (valid) {
                chain.doFilter(request, response);
            } else {
                LOGGER.log(Level.WARNING, "No valid crumb was included in request for {0}. Returning {1}.", new Object[] {httpRequest.getRequestURI(), HttpServletResponse.SC_FORBIDDEN});
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,"No valid crumb was included in the request");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private String extractCrumbFromRequest(HttpServletRequest httpRequest, String crumbFieldName) {
        String crumb = httpRequest.getHeader(crumbFieldName);
        if (crumb == null) {
            Enumeration<?> paramNames = httpRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = (String) paramNames.nextElement();
                if (crumbFieldName.equals(paramName)) {
                    crumb = httpRequest.getParameter(paramName);
                    break;
                }
            }
        }
        return crumb;
    }

    protected static boolean isMultipart(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        return MultipartFormDataParser.isMultiPartForm(request.getContentType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
    }

    private static final Logger LOGGER = Logger.getLogger(CrumbFilter.class.getName());
}
