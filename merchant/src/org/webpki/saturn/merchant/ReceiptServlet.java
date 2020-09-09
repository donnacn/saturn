/*
 *  Copyright 2015-2020 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.saturn.merchant;

import java.io.IOException;

import java.util.logging.Logger;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONOutputFormats;
import org.webpki.json.JSONParser;

public class ReceiptServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    static Logger logger = Logger.getLogger(ReceiptServlet.class.getName());

    public void doGet(HttpServletRequest request, HttpServletResponse response) 
    throws IOException, ServletException {
        String sequenceId = request.getPathInfo();
        if (!sequenceId.isEmpty()) {
            sequenceId = sequenceId.substring(1);
        }
        try {
            String receipt = DataBaseOperations.fetchReceipt(sequenceId);
            if (receipt == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.flushBuffer();
                return;
            }
            JSONObjectReader json = JSONParser.parse(receipt);
            HTML.debugPage(response, json.serializeToString(JSONOutputFormats.PRETTY_HTML), false);
        } catch (Exception e) {
            HTML.debugPage(response, e.getMessage(), false);
        }
    }
}
