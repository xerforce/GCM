/*
 * Copyright 2014 Wolfram Rittmeyer.
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

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.SmackException.NotConnectedException;
/**
 * Handles an echo request (message received from the client and resend it to  other devices.
 */
public class EchoProcessor implements PayloadProcessor{

    @Override
    public void handleMessage(CcsMessage msg) {
        PseudoDao dao = PseudoDao.getInstance();
        CcsServer ccsServer= CcsServer.getInstance();
        
        //to all the other registered devices
        Map<String, String> payload = new HashMap<String, String>();
		payload=msg.getPayload();
		String collapseKey = "sample";
		Long timeToLive = 1000L;
		Boolean delayWhileIdle = true;
		
		List<String> recipients=dao.getAllRegistrationIds();
		if(recipients.size()>1){
			for(String regId: recipients){
				if(regId.equals(msg.getFrom())){
					recipients.remove(regId);
				}
			}
		}
        ccsServer.sendBroadcast(payload, collapseKey, timeToLive, delayWhileIdle, recipients);
        
        //to current device
//        String jsonRequest = 
//                CcsServer.createJsonMessage(
//                        msg.getFrom(), 
//                        msgId, 
//                        msg.getPayload(), 
//                        null, 
//                        null, // TTL (null -> default-TTL) 
//                        false);
//        try {
//        	ccsServer.send(jsonRequest);
//		} catch (NotConnectedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
    }
}
