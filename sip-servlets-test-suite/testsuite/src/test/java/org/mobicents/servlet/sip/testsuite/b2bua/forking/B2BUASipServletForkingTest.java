/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.servlet.sip.testsuite.b2bua.forking;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.sip.SipProvider;

import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.log4j.Logger;
import org.mobicents.servlet.sip.SipServletTestCase;
import org.mobicents.servlet.sip.catalina.SipStandardManager;
import org.mobicents.servlet.sip.catalina.SipStandardService;
import org.mobicents.servlet.sip.startup.SipContextConfig;
import org.mobicents.servlet.sip.startup.SipStandardContext;
import org.mobicents.servlet.sip.testsuite.proxy.Shootist;
import org.mobicents.servlet.sip.testsuite.simple.forking.Proxy;
import org.mobicents.servlet.sip.testsuite.simple.forking.Shootme;

public class B2BUASipServletForkingTest extends SipServletTestCase {
	private static transient Logger logger = Logger.getLogger(B2BUASipServletForkingTest.class);		
	private static final int TIMEOUT = 40000;	
//	private static final int TIMEOUT = 100000000;
	
	public B2BUASipServletForkingTest(String name) {
		super(name);
		startTomcatOnStartup = false;
		autoDeployOnStartup = false;
		addSipConnectorOnStartup = false;
	}

	@Override
	public void deployApplication() {
		assertTrue(tomcat.deployContext(
				projectHome + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
				"sip-test-context", "sip-test"));
	}
	
	public SipStandardContext deployApplication(String name, String value) {
		SipStandardContext context = new SipStandardContext();
		context.setDocBase(projectHome + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp");
		context.setName("sip-test-context");
		context.setPath("sip-test");
		context.addLifecycleListener(new SipContextConfig());
		context.setManager(new SipStandardManager());
		ApplicationParameter applicationParameter = new ApplicationParameter();
		applicationParameter.setName(name);
		applicationParameter.setValue(value);
		context.addApplicationParameter(applicationParameter);
		assertTrue(tomcat.deployContext(context));
		return context;
	}
	
	public SipStandardContext deployApplication(Map<String, String> params) {
		SipStandardContext context = new SipStandardContext();
		context.setDocBase(projectHome + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp");
		context.setName("sip-test-context");
		context.setPath("sip-test");
		context.addLifecycleListener(new SipContextConfig());
		context.setManager(new SipStandardManager());
		for (Entry<String, String> param : params.entrySet()) {
			ApplicationParameter applicationParameter = new ApplicationParameter();
			applicationParameter.setName(param.getKey());
			applicationParameter.setValue(param.getValue());
			context.addApplicationParameter(applicationParameter);
		}
		assertTrue(tomcat.deployContext(context));
		return context;
	}
	
	public SipStandardContext deployApplicationServletListenerTest() {
		SipStandardContext context = new SipStandardContext();
		context.setDocBase(projectHome + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp");
		context.setName("sip-test-context");
		context.setPath("sip-test");
		context.addLifecycleListener(new SipContextConfig());
		context.setManager(new SipStandardManager());
		ApplicationParameter applicationParameter = new ApplicationParameter();
		applicationParameter.setName("testServletListener");
		applicationParameter.setValue("true");
		context.addApplicationParameter(applicationParameter);
		assertTrue(tomcat.deployContext(context));
		return context;
	}	

	@Override
	protected String getDarConfigurationFile() {
		return "file:///" + projectHome + "/sip-servlets-test-suite/testsuite/src/test/resources/" +
				"org/mobicents/servlet/sip/testsuite/callcontroller/call-forwarding-b2bua-servlet-dar.properties";
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();												
	}
	
	// non regression test for Issue 2354 http://code.google.com/p/mobicents/issues/detail?id=2354
	public void testB2BUAForking() throws Exception {		
        Shootme shootme1 = new Shootme(5080, true, 1500);
        SipProvider shootmeProvider = shootme1.createProvider();
        shootmeProvider.addSipListener(shootme1);
        Shootme shootme2 = new Shootme(5081, true, 2500);
        SipProvider shootme2Provider = shootme2.createProvider();
        shootme2Provider.addSipListener(shootme2);
		Proxy proxy = new Proxy(5070,2);
		SipProvider provider = proxy.createSipProvider();
        provider.addSipListener(proxy);
        Shootist shootist = new Shootist(true, "5060");
        shootist.pauseBeforeBye = 20000;
        shootist.setFromHost("sip-servlets.com");
        
        sipConnector = tomcat.addSipConnector(serverName, sipIpAddress, 5060, listeningPointTransport);
		tomcat.startTomcat();
		Map<String, String> params= new HashMap<String, String>();
		params.put("route", "sip:" + System.getProperty("org.mobicents.testsuite.testhostaddr") + ":5070");
		params.put("timeToWaitForBye", "20000");
		params.put("dontSetRURI", "true");
		SipStandardContext sipContext = deployApplication(params);
		shootist.init("forward-sender-forking-pending", false, null);
		Thread.sleep(TIMEOUT);
		proxy.stop();
		shootme1.stop();
		shootme2.stop();
		assertTrue(shootme1.isAckSeen());		
		assertTrue(shootme1.checkBye());
		assertTrue(shootme2.isAckSeen());
		assertTrue(shootme2.checkBye());	
		assertEquals(0, sipContext.getSipManager().getActiveSipSessions());
		assertEquals(0, sipContext.getSipManager().getActiveSipApplicationSessions());
		
	}
	
	@Override
	protected Properties getSipStackProperties() {
		Properties sipStackProperties = new Properties();
		sipStackProperties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT",
		"true");
		sipStackProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL",
				"32");
		sipStackProperties.setProperty(SipStandardService.DEBUG_LOG_STACK_PROP, 
				tomcatBasePath + "/" + "mss-jsip-" + getName() +"-debug.txt");
		sipStackProperties.setProperty(SipStandardService.SERVER_LOG_STACK_PROP,
				tomcatBasePath + "/" + "mss-jsip-" + getName() +"-messages.xml");
		sipStackProperties.setProperty("javax.sip.STACK_NAME", "mss-" + getName());
		sipStackProperties.setProperty(SipStandardService.AUTOMATIC_DIALOG_SUPPORT_STACK_PROP, "off");		
		sipStackProperties.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
		sipStackProperties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
		sipStackProperties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		sipStackProperties.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "5");
		sipStackProperties.setProperty(SipStandardService.LOOSE_DIALOG_VALIDATION, "true");
		sipStackProperties.setProperty(SipStandardService.PASS_INVITE_NON_2XX_ACK_TO_LISTENER, "true");
		return sipStackProperties;
	}
	
	@Override
	protected void tearDown() throws Exception {					
		logger.info("Test completed");
		super.tearDown();
	}
}