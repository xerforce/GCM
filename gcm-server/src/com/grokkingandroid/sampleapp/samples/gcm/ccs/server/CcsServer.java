/*
 * Copyright 2014 Wolfram Rittmeyer.
 *
 * Portions Copyright Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grokkingandroid.sampleapp.samples.gcm.ccs.server;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketInterceptor;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;
import javax.security.sasl.SaslException;

/**
 * Sample Smack implementation of a client for GCM Cloud Connection Server. Most
 * of it has been taken more or less verbatim from Googles documentation:
 * http://developer.android.com/google/gcm/ccs.html <br>
 * But some additions have been made. Bigger changes are annotated like that:
 * "/// new". <br>
 * Those changes have to do with parsing certain type of messages as well as
 * with sending messages to a list of recipients. The original code only covers
 * sending one message to exactly one recipient.
 */
public class CcsServer {

	public static final Logger logger = Logger.getLogger(CcsServer.class
			.getName());

	public static final String GCM_SERVER = "gcm.googleapis.com";
	public static final int GCM_PORT = 5235;

	public static final String GCM_ELEMENT_NAME = "gcm";
	public static final String GCM_NAMESPACE = "google:mobile:data";

	static Random random = new Random();
	XMPPConnection connection;
	ConnectionConfiguration config;

	// / new: some additional instance and class members
	private static CcsServer sInstance = null;
	private String mApiKey = null;
	private String mProjectId = null;
	private boolean mDebuggable = false;

	/**
	 * XMPP Packet Extension for GCM Cloud Connection Server.
	 */
	class GcmPacketExtension extends DefaultPacketExtension {
		String json;
		public GcmPacketExtension(String json) {
			super(GCM_ELEMENT_NAME, GCM_NAMESPACE);
			this.json = json;
		}

		public String getJson() {
			return json;
		}

		@Override
		public String toXML() {
			return String.format("<%s xmlns=\"%s\">%s</%s>", GCM_ELEMENT_NAME,
					GCM_NAMESPACE, json, GCM_ELEMENT_NAME);
		}

		@SuppressWarnings("unused")
		public Packet toPacket() {
			return new Message() {
				// Must override toXML() because it includes a <body>
				@Override
				public XmlStringBuilder toXML() {

					XmlStringBuilder buf = new XmlStringBuilder();
					buf.append("<message");
					if (getXmlns() != null) {
						buf.append(" xmlns=\"").append(getXmlns()).append("\"");
					}
					if (getLanguage() != null) {
						buf.append(" xml:lang=\"").append(getLanguage())
								.append("\"");
					}
					if (getPacketID() != null) {
						buf.append(" id=\"").append(getPacketID()).append("\"");
					}
					if (getTo() != null) {
						buf.append(" to=\"")
								.append(StringUtils.escapeForXML(getTo()))
								.append("\"");
					}
					if (getFrom() != null) {
						buf.append(" from=\"")
								.append(StringUtils.escapeForXML(getFrom()))
								.append("\"");
					}
					buf.append(">");
					buf.append(GcmPacketExtension.this.toXML());
					buf.append("</message>");
					return buf;
				}
			};
		}
	}

	public static CcsServer getInstance() {
		if (sInstance == null) {
			throw new IllegalStateException(
					"You have to prepare the client first");
		}
		return sInstance;
	}

	public static CcsServer prepareClient(String projectId, String apiKey,
			boolean debuggable) {
		synchronized (CcsServer.class) {
			if (sInstance == null) {
				sInstance = new CcsServer(projectId, apiKey, debuggable);
			}
		}
		return sInstance;
	}

	private CcsServer(String projectId, String apiKey, boolean debuggable) {
		this();
		mApiKey = apiKey;
		mProjectId = projectId;
		mDebuggable = debuggable;
	}

	private CcsServer() {
		// Add GcmPacketExtension
		ProviderManager.addExtensionProvider(GCM_ELEMENT_NAME, GCM_NAMESPACE,
				new PacketExtensionProvider() {

					@Override
					public PacketExtension parseExtension(XmlPullParser parser)
							throws Exception {
						String json = parser.nextText();
						GcmPacketExtension packet = new GcmPacketExtension(json);
						return packet;
					}
				});
	}

	/**
	 * Returns a random message id to uniquely identify a message.
	 * 
	 * <p>
	 * Note: This is generated by a pseudo random number generator for
	 * illustration purpose, and is not guaranteed to be unique.
	 * 
	 */
	public String getRandomMessageId() {
		return "m-" + Long.toString(random.nextLong());
	}

	/**
	 * Sends a downstream GCM message.
	 * 
	 * @throws NotConnectedException
	 */
	public void send(String jsonRequest) throws NotConnectedException {
		Packet request = new GcmPacketExtension(jsonRequest).toPacket();
		connection.sendPacket(request);
	}

	// / new: for sending messages to a list of recipients
	/**
	 * Sends a message to multiple recipients. Kind of like the old HTTP message
	 * with the list of regIds in the "registration_ids" field.
	 */
	public void sendBroadcast(Map<String, String> payload, String collapseKey,
			long timeToLive, Boolean delayWhileIdle, List<String> recipients) {
		Map map = createAttributeMap(null, null, payload, collapseKey,
				timeToLive, delayWhileIdle);
		for (String toRegId : recipients) {
			String messageId = getRandomMessageId();
			map.put("message_id", messageId);
			map.put("to", toRegId);
			String jsonRequest = createJsonMessage(map);
			try {
				send(jsonRequest);
			} catch (NotConnectedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// / new: customized version of the standard handleIncomingDateMessage
	// method
	/**
	 * Handles an upstream data message from a device application.
	 */
	public void handleIncomingDataMessage(CcsMessage msg) {
		if (msg.getPayload().get("action") != null) {
			PayloadProcessor processor = ProcessorFactory.getProcessor(msg
					.getPayload().get("action"));
			processor.handleMessage(msg);
		}
	}

	// / new: was previously part of the previous method
	/**
     * 
     */
	private CcsMessage getMessage(Map<String, Object> jsonObject) {
		String from = jsonObject.get("from").toString();

		// PackageName of the application that sent this message.
		String category = jsonObject.get("category").toString();

		// unique id of this message
		String messageId = jsonObject.get("message_id").toString();

		@SuppressWarnings("unchecked")
		Map<String, String> payload = (Map<String, String>) jsonObject
				.get("data");

		CcsMessage msg = new CcsMessage(from, category, messageId, payload);

		return msg;
	}

	/**
	 * Handles an ACK.
	 * 
	 * <p>
	 * By default, it only logs a INFO message, but subclasses could override it
	 * to properly handle ACKS.
	 */
	public void handleAckReceipt(Map<String, Object> jsonObject) {
		String messageId = jsonObject.get("message_id").toString();
		String from = jsonObject.get("from").toString();
		System.out.println("from====="+from);
		logger.log(Level.INFO, "handleAckReceipt() from: " + from
				+ ", messageId: " + messageId);
	}

	/**
	 * Handles a NACK.
	 * 
	 * <p>
	 * By default, it only logs a INFO message, but subclasses could override it
	 * to properly handle NACKS.
	 */
	public void handleNackReceipt(Map<String, Object> jsonObject) {
		String messageId = jsonObject.get("message_id").toString();
		String from = jsonObject.get("from").toString();
		logger.log(Level.INFO, "handleNackReceipt() from: " + from
				+ ", messageId: " + messageId);
	}

	/**
	 * Creates a JSON encoded GCM message.
	 * 
	 * @param to
	 *            RegistrationId of the target device (Required).
	 * @param messageId
	 *            Unique messageId for which CCS will send an "ack/nack"
	 *            (Required).
	 * @param payload
	 *            Message content intended for the application. (Optional).
	 * @param collapseKey
	 *            GCM collapse_key parameter (Optional).
	 * @param timeToLive
	 *            GCM time_to_live parameter (Optional).
	 * @param delayWhileIdle
	 *            GCM delay_while_idle parameter (Optional).
	 * @return JSON encoded GCM message.
	 */
	public static String createJsonMessage(String to, String messageId,
			Map<String, String> payload, String collapseKey, Long timeToLive,
			Boolean delayWhileIdle) {
		return createJsonMessage(createAttributeMap(to, 
				messageId, 
				payload,
				collapseKey, 
				timeToLive, 
				delayWhileIdle));
	}

	public static String createJsonMessage(Map map) {
		return JSONValue.toJSONString(map);
	}

	public static Map createAttributeMap(String to, String messageId,
			Map<String, String> payload, String collapseKey, Long timeToLive,
			Boolean delayWhileIdle) {
		Map<String, Object> message = new HashMap<String, Object>();
		if (to != null) {
			message.put("to", to);
		}
		if (collapseKey != null) {
			message.put("collapse_key", collapseKey);
		}
		if (timeToLive != null) {
			message.put("time_to_live", timeToLive);
		}
		if (delayWhileIdle != null && delayWhileIdle) {
			message.put("delay_while_idle", true);
		}
		if (messageId != null) {
			message.put("message_id", messageId);
		}
		message.put("data", payload);
		return message;
	}

	/**
	 * Creates a JSON encoded ACK message for an upstream message received from
	 * an application.
	 * 
	 * @param to
	 *            RegistrationId of the device who sent the upstream message.
	 * @param messageId
	 *            messageId of the upstream message to be acknowledged to CCS.
	 * @return JSON encoded ack.
	 */
	public static String createJsonAck(String to, String messageId) {
		Map<String, Object> message = new HashMap<String, Object>();
		message.put("message_type", "ack");
		message.put("to", to);
		message.put("message_id", messageId);
		return JSONValue.toJSONString(message);
	}

	// / new: NACK added
	/**
	 * Creates a JSON encoded NACK message for an upstream message received from
	 * an application.
	 * 
	 * @param to
	 *            RegistrationId of the device who sent the upstream message.
	 * @param messageId
	 *            messageId of the upstream message to be acknowledged to CCS.
	 * @return JSON encoded ack.
	 */
	public static String createJsonNack(String to, String messageId) {
		Map<String, Object> message = new HashMap<String, Object>();
		message.put("message_type", "ack");
		message.put("to", to);
		message.put("message_id", messageId);
		return JSONValue.toJSONString(message);
	}

	/**
	 * Connects to GCM Cloud Connection Server using the supplied credentials.
	 * 
	 * @throws XMPPException
	 */
	public void connect() throws XMPPException {
		config = new ConnectionConfiguration(GCM_SERVER, GCM_PORT);
		config.setSecurityMode(SecurityMode.enabled);
		config.setReconnectionAllowed(true);
		config.setRosterLoadedAtLogin(false);
		config.setSendPresence(false);
		config.setSocketFactory(SSLSocketFactory.getDefault());

		// NOTE: Set to true to launch a window with information about packets
		// sent and received
		config.setDebuggerEnabled(mDebuggable);

		// -Dsmack.debugEnabled=true
		// XMPPTCPConnection.DEBUG_ENABLED = true;

		// connection=new XMPPConnection(config); this class has been replaced
		// by XMPPTCPConnection
		connection = new XMPPTCPConnection(config);

		try {
			connection.connect();
		} catch (SmackException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		connection.addConnectionListener(new ConnectionListener() {

			@Override
			public void reconnectionSuccessful() {
				logger.info("Reconnecting..");
			}

			@Override
			public void reconnectionFailed(Exception e) {
				logger.log(Level.INFO, "Reconnection failed.. ", e);
			}

			@Override
			public void reconnectingIn(int seconds) {
				logger.log(Level.INFO, "Reconnecting in %d secs", seconds);
			}

			@Override
			public void connectionClosedOnError(Exception e) {
				logger.log(Level.INFO, "Connection closed on error.");
			}

			@Override
			public void connectionClosed() {
				logger.info("Connection closed.");
			}

			@Override
			public void authenticated(XMPPConnection arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void connected(XMPPConnection arg0) {
				// TODO Auto-generated method stub

			}
		});

		// Handle incoming packets
		connection.addPacketListener(new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				logger.log(Level.INFO, "Received: " + packet.toXML());
				Message incomingMessage = (Message) packet;
				GcmPacketExtension gcmPacket = (GcmPacketExtension) incomingMessage.getExtension(GCM_NAMESPACE);
				String json = gcmPacket.getJson();
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> jsonMap = (Map<String, Object>) JSONValue
							.parseWithException(json);

					handleMessage(jsonMap);
				} catch (ParseException e) {
					logger.log(Level.SEVERE, "Error parsing JSON " + json, e);
				} catch (Exception e) {
					logger.log(Level.SEVERE, "Couldn't send echo.", e);
				}
			}
		}, new PacketTypeFilter(Message.class));

		// Log all outgoing packets
		connection.addPacketInterceptor(new PacketInterceptor() {
			@Override
			public void interceptPacket(Packet packet) {
				logger.log(Level.INFO, "Sent: {0}", packet.toXML());
			}
		}, new PacketTypeFilter(Message.class));

		try {
			connection.login(mProjectId + "@gcm.googleapis.com", mApiKey);
		} catch (SaslException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SmackException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		logger.log(Level.INFO, "logged in: " + mProjectId);
	}

	private void handleMessage(Map<String, Object> jsonMap) {
		// present for "ack"/"nack", null otherwise
		Object messageType = jsonMap.get("message_type");

		if (messageType == null) {
			CcsMessage msg = getMessage(jsonMap);
			// Normal upstream data message
			try {
				handleIncomingDataMessage(msg);
				// Send ACK to CCS
				String ack = createJsonAck(msg.getFrom(), msg.getMessageId());
//				String messageContent=msg.getPayload().
				send(ack);
			} catch (Exception e) {
				// Send NACK to CCS
				String nack = createJsonNack(msg.getFrom(), msg.getMessageId());
				try {
					send(nack);
				} catch (NotConnectedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		} else if ("ack".equals(messageType.toString())) {
			// Process Ack
			handleAckReceipt(jsonMap);
		} else if ("nack".equals(messageType.toString())) {
			// Process Nack
			handleNackReceipt(jsonMap);
		} else {
			logger.log(Level.WARNING, "Unrecognized message type (%s)",
					messageType.toString());
		}
	}

	public static void main(String[] args) {
		final String projectId = args[0];// ProjectID obtained from the
											// "Google Developers Console"
		final String password = args[1];// API kEY obtained from the
										// "Google Developers Console"
		CcsServer ccsServer = CcsServer.prepareClient(projectId, password, true);

		try {
			ccsServer.connect();
		} catch (XMPPException e) {
			e.printStackTrace();
		}

		// Send a sample hello downstream message to a device.
		if(args.length>2){
			final String toRegId = args[2];
			String messageId = ccsServer.getRandomMessageId();
			Map<String, String> payload = new HashMap<String, String>();
			payload.put("msg", "Simple sample sessage");
			String collapseKey = "sample";
			Long timeToLive = 1000L;
			Boolean delayWhileIdle = true;
			try {
				ccsServer.send(createJsonMessage(toRegId, messageId, payload,
						collapseKey, timeToLive, delayWhileIdle));
			} catch (NotConnectedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}