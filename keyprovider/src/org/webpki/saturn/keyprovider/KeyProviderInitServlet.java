/*
 *  Copyright 2015-2018 WebPKI.org (http://webpki.org).
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

package org.webpki.saturn.keyprovider;

import java.io.IOException;

import java.util.logging.Logger;

import java.net.URLEncoder;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.webpki.keygen2.ServerState;

import org.webpki.net.MobileProxyParameters;

public class KeyProviderInitServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static Logger logger = Logger.getLogger(KeyProviderInitServlet.class.getCanonicalName());

    static final String KEYGEN2_SESSION_ATTR           = "keygen2";
    static final String USERNAME_SESSION_ATTR          = "userName";
    
    static final int NAME_MAX_LENGTH                   = 50;  // Reflected in the DB

    static final String INIT_TAG = "init";     // Note: This is currently also a part of the KeyGen2 client!
    static final String ABORT_TAG = "abort";
    static final String PARAM_TAG = "msg";
    static final String ERROR_TAG = "err";
    
    static final String GO_HOME =              
            "history.pushState(null, null, 'init');\n" +
            "window.addEventListener('popstate', function(event) {\n" +
            "    history.pushState(null, null, 'init');\n" +
            "});\n";

    static final String HTML_INIT = 
            "<!DOCTYPE html><html>" + 
            "<head>" + 
            "<link rel=\"icon\" href=\"saturn.png\" sizes=\"192x192\">" + 
            "<title>Payment Credential Enrollment</title>" + 
            "<meta charset=\"utf-8\">" + 
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + 
            "<style type=\"text/css\">" + 
            ".displayContainer {" + 
            "    display: block;" + 
            "    height: 100%;" + 
            "    width: 100%;" + 
            "    align-items: center;" + 
            "    display: flex;" + 
            "    flex-direction: column;" + 
            "    justify-content: center;" + 
            "}" +
            
            ".link {" +
            "  font-weight:bold;" +
            "  font-size:8pt;" +
            "  color:blue;" +
            "  font-family:arial,verdana;text-decoration:none;" +
            "}" +

            ".stdbtn {" + 
            "  cursor:pointer;" + 
            "  background:linear-gradient(to bottom, #eaeaea 14%,#fcfcfc 52%,#e5e5e5 89%);" + 
            "  border-width:1px;" + 
            "  border-style:solid;" + 
            "  border-color:#a9a9a9;" + 
            "  border-radius:5pt;" + 
            "  padding:3pt 10pt;" + 
            "  box-shadow:3pt 3pt 3pt #d0d0d0;" + 
            "}" +
 
            ".label, .stdbtn {" +
            "  font-family:Arial,'Liberation Sans',Verdana,'Bitstream Vera Sans','DejaVu Sans';" + 
            "  font-size:11pt;" + 
            "}" +
            
            "body {" + 
            "    font-size:10pt;" + 
            "    color:#000000;" + 
            "    font-family:verdana,arial;" + 
            "    background-color: white;" + 
            "    height: 100%;" + 
            "    margin: 0;" + 
            "    width: 100%;" + 
            "}" + 

            "html {" + 
            "    height: 100%;" + 
            "    width: 100%;" + 
            "}" +

            ".sitefooter {" + 
            "  display:flex;" + 
            "  align-items:center;" + 
            "  border-width:1px 0 0 0;" + 
            "  border-style:solid;" + 
            "  border-color:#a9a9a9;" + 
            "  position:absolute;" + 
            "  z-index:5;" + 
            "  left:0px;" + 
            "  bottom:0px;" + 
            "  right:0px;" + 
            "  background-color:#ffffe0;" + 
            "  padding:0.3em 0.7em;" + 
            "}" +

            "@media (max-width:768px) {" +
 
            "  .stdbtn {" + 
            "    box-shadow:2pt 2pt 2pt #d0d0d0;" + 
            "  }" +

            "  body {" + 
            "    font-size:8pt;" + 
            "  }" +

            "}" +

            "</style>";

    static String getHTML(String javascript, String bodyscript, String box) {
        StringBuilder s = new StringBuilder(HTML_INIT);
        if (javascript != null) {
            s.append("<script type=\"text/javascript\">").append(javascript)
                    .append("</script>");
        }
        s.append("</head><body");
        if (bodyscript != null) {
            s.append(' ').append(bodyscript);
        }
        s.append(
                "><div style=\"cursor:pointer;position:absolute;top:15pt;left:15pt;z-index:5;width:100pt\"" +
                " onclick=\"document.location.href='http://cyberphone.github.io/doc/saturn'\" title=\"Home of Saturn\">")
         .append (KeyProviderService.saturnLogotype)
         .append ("</div><div class=\"displayContainer\">")
                .append(box).append("</div></body></html>");
        System.out.println(s.toString());
        return s.toString();
    }
  
    static void output(HttpServletResponse response, String html) throws IOException, ServletException {
        response.setContentType("text/html; charset=utf-8");
        response.setHeader("Pragma", "No-Cache");
        response.setDateHeader("EXPIRES", 0);
        response.getOutputStream().write(html.getBytes("UTF-8"));
    }
    
    static String getInvocationUrl(String scheme, HttpSession session) throws IOException {
        ////////////////////////////////////////////////////////////////////////////////////////////
        // The following is the actual contract between an issuing server and a KeyGen2 client.
        // The "cookie" element is optional while the "url" argument is mandatory.
        // The "init" argument bootstraps the protocol via an HTTP GET
        ////////////////////////////////////////////////////////////////////////////////////////////
        String urlEncoded = URLEncoder.encode(KeyProviderService.keygen2RunUrl, "utf-8");
        return scheme + "://" + MobileProxyParameters.HOST_KEYGEN2 + 
                "?cookie=JSESSIONID%3D" + session.getId() +
               "&url=" + urlEncoded +
               "&ver=" + KeyProviderService.grantedVersions +
               "&init=" + urlEncoded + "%3F" + INIT_TAG + "%3Dtrue" +
               "&cncl=" + urlEncoded + "%3F" + ABORT_TAG + "%3Dtrue";
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!request.getHeader("User-Agent").contains("Android")) {
            output(response, 
                    getHTML(null,
                            null,
                             "<div class=\"label\">This proof-of-concept system only supports Android</div>"));
            return;
        }
        request.setCharacterEncoding("utf-8");
        String userName = request.getParameter(USERNAME_SESSION_ATTR);
        if (userName == null) {
            userName = "Luke Skywalker &#129412;";
        }

        HttpSession session = request.getSession(true);

        output(response, 
               getHTML(KeyProviderService.useW3cPaymentRequest ? 
                       "function buildPaymentRequest() {\n" +
                       "  if (!window.PaymentRequest) {\n" +
                       "    return null;\n" +
                       "  }\n\n" +
                       "  const details = {\n" +
                       "    total: {\n" +
                       "      label: 'total', \n" +
                       "      amount: {\n" +
                       "        currency: 'USD',\n" +
                       "        value: '1.00'\n" +
                       "      }\n" +
                       "    }\n" +
                       "  };\n\n" +
                       "  const supportedInstruments = [{\n" +
                       "    supportedMethods: '" + KeyProviderService.w3cPaymentRequestMethod + "',\n" +
                       "    data: {url: '" + getInvocationUrl(MobileProxyParameters.SCHEME_W3CPAY, 
                                                              session) + "'}\n" +
                       "  }];\n\n" +
                       "  let request = null;\n\n" +
                       "  try {\n" +
                       "    request = new PaymentRequest(supportedInstruments, details);\n" +
                       "    if (request.canMakePayment) {\n" +
                       "      request.canMakePayment().then(function(result) {\n" +
                       "        console.info(result ? 'Can make payment' : 'Cannot make payment');\n" +
                       "      }).catch(function(err) {\n" +
                       "        console.info(err);\n" +
                       "      });\n" +
                       "    }\n" +
                       "  } catch (e) {\n" +
                       "    console.info('Developer mistake:' + e.message);\n" +
                       "  }\n" +
                       "  return request;\n" +
                       "}\n\n" +
                       "let w3creq = buildPaymentRequest();\n\n" +
                       "function enroll() {\n" +
                       "  if (w3creq) {\n" +
                       "    try {\n" +
                       "      w3creq.show().then(function(response) {\n" +
                       "        response.complete('success');\n" +
                       "        console.info('Success!');\n" +
                       "        document.location.href = response.details.goto;\n" +
                       "      }).catch(function(err) {\n" +
                       "        console.info(err.message);\n" +
                        "      });\n" +
                       "    } catch (e) {\n" +
                       "      console.info('Developer mistake:' + e.message);\n" +
                       "    }\n" +
                       "  } else {\n" +
                       "    console.log('Mobile application is supposed to start here');\n" +
                       "    document.forms.shoot.submit();\n" +
                       "  }\n" +
                       "}\n"
                         :
                       "function enroll() {\n" +
                       "  console.log('Mobile application is supposed to start here');\n" +
                       "  document.forms.shoot.submit();\n" +
                       "}",
                       null,
                       "<form name=\"shoot\" method=\"POST\" action=\"init\">" + 
                       "<div>This proof-of-concept system provisions secure payment credentials<br>" + 
                       "to be used in the Android version of the Saturn &quot;Wallet&quot;.</div>" + 
                       "<div style=\"display:flex;justify-content:center;padding-top:15pt\"><table>" + 
                       "     <tr><td>Your name (real or made up):</td></tr>" + 
                       "     <tr><td><input type=\"text\" name=\"" + USERNAME_SESSION_ATTR + 
                       "\" value=\"" + userName + "\" size=\"30\" maxlength=\"50\" " + 
                       "style=\"background-color:#def7fc\"></td></tr>" + 
                       "</table></div>" + 
                       "<div style=\"text-align:center\">This name will be printed on your virtual payment cards.</div>" + 
                       "<div style=\"display:flex;justify-content:center;padding-top:15pt\">" +
                       "<div class=\"stdbtn\" onclick=\"enroll()\">Start Enrollment</div></div>" + 
                       "<div style=\"padding-top:40pt;padding-bottom:10pt\">If you have not already " +
                       "installed Saturn, this is the time to do it!</div>" +
                       "<div style=\"cursor:pointer;display:flex;justify-content:center;align-items:center\">" +
                       "<img src=\"google-play-badge.png\" style=\"height:25pt;padding:0 15pt\" alt=\"image\" " +
                       "title=\"Android\" onclick=\"document.location.href = " +
                       "'https://play.google.com/store/apps/details?id=org.webpki.mobile.android'\"></div>" + 
                       "</form>" + 
                       "</div>" + 
                       "<div class=\"sitefooter\">Note: in a real configuration you would also need to " +
                       "authenticate as a part of the enrollment."));
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.setCharacterEncoding("utf-8");
        String userName = request.getParameter(USERNAME_SESSION_ATTR);
        if (userName == null || (userName = userName.trim()).isEmpty()) {
            response.sendRedirect("init");
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect("init?" + USERNAME_SESSION_ATTR + 
                                  "=" + URLEncoder.encode(userName, "utf-8"));
            return;
        }
        if (userName.length() > NAME_MAX_LENGTH) {
            userName = userName.substring(0, NAME_MAX_LENGTH);
        }
        session.setAttribute(KEYGEN2_SESSION_ATTR,
                new ServerState(new KeyGen2SoftHSM(KeyProviderService.keyManagementKey), 
                                KeyProviderService.keygen2RunUrl,
                                KeyProviderService.serverCertificate,
                                null));
        session.setAttribute(USERNAME_SESSION_ATTR, userName);
        output(response,
               getHTML(GO_HOME,
                       "onload=\"document.location.href = '" + 
                           getInvocationUrl(MobileProxyParameters.SCHEME_URLHANDLER, session) + 
                           "#Intent;scheme=webpkiproxy;package=" +  MobileProxyParameters.ANDROID_PACKAGE_NAME +
                           ";end';\"", 
                       "<div><div class=\"label\" style=\"text-align:center\">Saturn App Bootstrap</div>" +
                       "<div style=\"padding-top:15pt\">If this is all you get there is " +
                       "something wrong with the installation.</div>" +
                       "</div>"));
    }
}
