//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.servlet;

import java.io.IOException;
import java.time.Duration;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.FrameHandler;

/**
 * Abstract Servlet used to bridge the Servlet API to the WebSocket API.
 * <p>
 * To use this servlet, you will be required to register your websockets with the {@link WebSocketMapping} so that it can create your websockets under the
 * appropriate conditions.
 * <p>
 * The most basic implementation would be as follows.
 * <p>
 * <pre>
 * package my.example;
 *
 * import WebSocketServlet;
 * import WebSocketServletFactory;
 *
 * public class MyEchoServlet extends WebSocketServlet
 * {
 *     &#064;Override
 *     public void configure(FrameHandler.Configuration configuration)
 *     {
 *       configuration.setMaxFrameSize(4096);
 *       addMapping("/",(req,res)->new EchoSocket());
 *     }
 * }
 * </pre>
 * <p>
 * Note: that only request that conforms to a "WebSocket: Upgrade" handshake request will trigger the {@link WebSocketMapping} handling of creating
 * WebSockets.<br>
 * All other requests are treated as normal servlet requests.
 * <p>
 * <p>
 * <b>Configuration / Init-Parameters:</b><br>
 * <p>
 * <dl>
 * <dt>maxIdleTime</dt>
 * <dd>set the time in ms that a websocket may be idle before closing<br>
 * <p>
 * <dt>maxTextMessageSize</dt>
 * <dd>set the size in UTF-8 bytes that a websocket may be accept as a Text Message before closing<br>
 * <p>
 * <dt>maxBinaryMessageSize</dt>
 * <dd>set the size in bytes that a websocket may be accept as a Binary Message before closing<br>
 * <p>
 * <dt>inputBufferSize</dt>
 * <dd>set the size in bytes of the buffer used to read raw bytes from the network layer<br>
 * </dl>
 */
@SuppressWarnings("serial")
public abstract class WebSocketServlet extends HttpServlet
{
    private static final Logger LOG = Log.getLogger(WebSocketServlet.class);
    private final CustomizedWebSocketServletFactory customizer = new CustomizedWebSocketServletFactory();

    private WebSocketMapping mapping;

    public abstract void configure(WebSocketServletFactory factory);

    @Override
    public void init() throws ServletException
    {
        try
        {
            ServletContext servletContext = getServletContext();

            mapping = WebSocketMapping.ensureMapping(servletContext);

            String max = getInitParameter("maxIdleTime");
            if (max != null)
                customizer.setIdleTimeout(Duration.ofMillis(Long.parseLong(max)));

            max = getInitParameter("maxTextMessageSize");
            if (max != null)
                customizer.setMaxTextMessageSize(Long.parseLong(max));

            max = getInitParameter("maxBinaryMessageSize");
            if (max != null)
                customizer.setMaxBinaryMessageSize(Long.parseLong(max));

            max = getInitParameter("inputBufferSize");
            if (max != null)
                customizer.setInputBufferSize(Integer.parseInt(max));

            max = getInitParameter("outputBufferSize");
            if (max != null)
                customizer.setOutputBufferSize(Integer.parseInt(max));

            max = getInitParameter("maxFrameSize");
            if (max==null)
                max = getInitParameter("maxAllowedFrameSize");
            if (max != null)
                customizer.setMaxFrameSize(Long.parseLong(max));

            String autoFragment = getInitParameter("autoFragment");
            if (autoFragment != null)
                customizer.setAutoFragment(Boolean.parseBoolean(autoFragment));

            configure(customizer); // Let user modify customizer prior after init params
        }
        catch (Throwable x)
        {
            throw new ServletException(x);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        // Typically this servlet is used together with the WebSocketUpgradeFilter,
        // so upgrade requests will normally be upgraded by the filter.  But we
        // can do it here as well if for some reason the filter did not match.

        if (mapping.upgrade(req, resp, null))
            return;

        // If we reach this point, it means we had an incoming request to upgrade
        // but it was either not a proper websocket upgrade, or it was possibly rejected
        // due to incoming request constraints (controlled by WebSocketCreator)
        if (resp.isCommitted())
            return;

        // Handle normally
        super.service(req, resp);
    }


    private class CustomizedWebSocketServletFactory extends FrameHandler.ConfigurationCustomizer implements WebSocketServletFactory
    {
        @Override
        public Duration getDefaultIdleTimeout()
        {
            return getIdleTimeout();
        }

        @Override
        public void setDefaultIdleTimeout(Duration duration)
        {
            setIdleTimeout(duration);
        }

        @Override
        public int getDefaultInputBufferSize()
        {
            return getInputBufferSize();
        }

        @Override
        public void setDefaultInputBufferSize(int bufferSize)
        {
            setInputBufferSize(bufferSize);
        }

        @Override
        public long getDefaultMaxAllowedFrameSize()
        {
            return getMaxFrameSize();
        }

        @Override
        public void setDefaultMaxAllowedFrameSize(long maxFrameSize)
        {
            setMaxFrameSize(maxFrameSize);
        }

        @Override
        public long getDefaultMaxBinaryMessageSize()
        {
            return getMaxBinaryMessageSize();
        }

        @Override
        public void setDefaultMaxBinaryMessageSize(long size)
        {
            setMaxBinaryMessageSize(size);
        }

        @Override
        public long getDefaultMaxTextMessageSize()
        {
            return getMaxTextMessageSize();
        }

        @Override
        public void setDefaultMaxTextMessageSize(long size)
        {
            setMaxTextMessageSize(size);
        }

        @Override
        public int getDefaultOutputBufferSize()
        {
            return getOutputBufferSize();
        }

        @Override
        public void setDefaultOutputBufferSize(int bufferSize)
        {
            setOutputBufferSize(bufferSize);
        }

        @Override
        public void addMapping(String pathSpec, WebSocketCreator creator)
        {
            addMapping(WebSocketMapping.parsePathSpec(pathSpec), creator);
        }

        @Override
        public void addMapping(PathSpec pathSpec, WebSocketCreator creator)
        {
            // TODO a bit fragile. This code knows that only the JettyFHF is added directly as a been
            ServletContext servletContext = getServletContext();
            ContextHandler contextHandler = ContextHandler.getContextHandler(servletContext);
            FrameHandlerFactory frameHandlerFactory = contextHandler.getBean(FrameHandlerFactory.class);

            if (frameHandlerFactory==null)
                throw new IllegalStateException("No known FrameHandlerFactory");

            mapping.addMapping(pathSpec, creator, frameHandlerFactory, this);
        }

        @Override
        public WebSocketCreator getMapping(PathSpec pathSpec)
        {
            return mapping.getMapping(pathSpec);
        }

        @Override
        public WebSocketCreator getMatch(String target)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeMapping(PathSpec pathSpec)
        {
            return mapping.removeMapping(pathSpec);
        }

        @Override
        public PathSpec parsePathSpec(String pathSpec)
        {
            return WebSocketMapping.parsePathSpec(pathSpec);
        }
    }
}
