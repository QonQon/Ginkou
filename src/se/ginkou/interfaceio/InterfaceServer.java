/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package se.ginkou.interfaceio;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BufferingHttpServiceHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;

/**
 * Basic, yet fully functional and spec compliant, HTTP/1.1 server based on the non-blocking 
 * I/O model.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP server. 
 * 
 *
 */
public class InterfaceServer {
	
	public static final int PORT = 38602;

    public static void main(String[] args) throws Exception {
        startServer();
    }
    
    public static void startServer() throws Exception {
    	HttpParams params = new SyncBasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

        HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });
        
        BufferingHttpServiceHandler handler = new BufferingHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                params);

        // Set up request handlers
        HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
        reqistry.register("*", new HttpJSONHandler());

        handler.setHandlerResolver(reqistry);

        // Provide an event logger
        handler.setEventListener(new EventLogger());

        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        ListeningIOReactor ioReactor = new DefaultListeningIOReactor(2, params);
        try {
            ioReactor.listen(new InetSocketAddress(PORT));
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }

    static class HttpJSONHandler implements HttpRequestHandler  {

        public HttpJSONHandler() {
            super();
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

        	String inString = null;
        	
        	// Print headers
        	Header[] headers = request.getAllHeaders();
        	for (Header aHeader : headers) {
        		System.out.println(aHeader.toString());
        	}
        	
        	
        	// Check that we support the HTTP method
            String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported");
            }

            // Save incoming POST data
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                byte[] entityContent = EntityUtils.toByteArray(entity);
                inString = new String(entityContent);
                System.out.println("Incoming entity content (bytes): " + entityContent.length);
                System.out.println("Incoming entity: " + inString);
            }

            
            String path = request.getRequestLine().getUri();
            if (!existsPath(path)) {

                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                NStringEntity entity = new NStringEntity(
                        "Path " + path + " not found", "UTF-8");
                entity.setContentType("text/html; charset=UTF-8");
                response.setEntity(entity);
                System.out.println("Path " + path + " not found");

            } else if (!userMayAccess(path)) {

                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                NStringEntity entity = new NStringEntity(
                        "<html><body><h1>Access denied</h1></body></html>",
                        "UTF-8");
                entity.setContentType("text/html; charset=UTF-8");
                response.setEntity(entity);
                System.out.println("User forbidden to access " + path);

            } else {

            	if (method.equals("GET")) {
            		if(path.equals("/dummybank")) {
            			final File file = new File(".", "dummypage.html");
            			response.setStatusCode(HttpStatus.SC_OK);
                        NFileEntity body = new NFileEntity(file, "text/html");
                        response.setEntity(body);
                        System.out.println("Serving file " + file.getPath());
            		} 
            	}
            	
            	if(method.equals("POST")){
            		if(path.equals("/ping")){
            			response.setStatusCode(HttpStatus.SC_OK);
                        NStringEntity body = new NStringEntity(inString, "UTF-8");
                        body.setContentType("text/json; charset=UTF-8");
                        response.setEntity(body);
                        System.out.println("Responding to " + path);
            		} else if (path.equals("/datatables")) { 
            			response.setStatusCode(HttpStatus.SC_OK);
            			response.addHeader("Access-Control-Allow-Origin", "*");
            			DataTablesInterface DTInterface = new DataTablesInterface(inString);
                        NStringEntity body = new NStringEntity(DTInterface.getResponse(), "UTF-8");
                        body.setContentType("text/json; charset=UTF-8");
                        response.setEntity(body);
                        System.out.println("Responding to " + path);
            		}
                }
            }
        }

		private boolean userMayAccess(String path) {
			// TODO Auto-generated method stub
			return true;
		}

		private boolean existsPath(String path) {
			// TODO Auto-generated method stub
			return true;
		}

    }

    static class EventLogger implements EventListener {

        public void connectionOpen(final NHttpConnection conn) {
            System.out.println("Connection open: " + conn);
        }

        public void connectionTimeout(final NHttpConnection conn) {
            System.out.println("Connection timed out: " + conn);
        }

        public void connectionClosed(final NHttpConnection conn) {
            System.out.println("Connection closed: " + conn);
        }

        public void fatalIOException(final IOException ex, final NHttpConnection conn) {
            System.err.println("I/O error: " + ex.getMessage());
        }

        public void fatalProtocolException(final HttpException ex, final NHttpConnection conn) {
            System.err.println("HTTP error: " + ex.getMessage());
        }

    }

}
