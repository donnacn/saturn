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
package org.webpki.saturn.keyprovider;

import java.io.IOException;

import java.math.BigDecimal;

import java.net.URLEncoder;

import java.security.KeyPair;
import java.security.PrivateKey;

import java.util.GregorianCalendar;
import java.util.LinkedHashMap;

import java.util.logging.Logger;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.webpki.crypto.HashAlgorithms;

import org.webpki.json.JSONArrayWriter;
import org.webpki.json.JSONCryptoHelper;
import org.webpki.json.JSONDecryptionDecoder;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONOutputFormats;
import org.webpki.json.JSONParser;

import org.webpki.net.MobileProxyParameters;
import org.webpki.saturn.common.AuthorizationData;
import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.Currencies;
import org.webpki.saturn.common.Messages;
import org.webpki.saturn.common.NonDirectPayments;
import org.webpki.saturn.common.PaymentMethods;
import org.webpki.saturn.common.PaymentRequest;
import org.webpki.saturn.common.TimeUtils;

import org.webpki.util.Base64;
import org.webpki.util.PEMDecoder;

import org.webpki.webutil.ServletUtil;

public class WalletUiTestServlet extends HttpServlet implements BaseProperties {

    private static final long serialVersionUID = 1L;
    
    static Logger logger = Logger.getLogger(WalletUiTestServlet.class.getName ());
    
    private static final String W3C_MODE = "w3c";

    private static final String TYPE = "type";
    
    private static final String KEY =  "key";
    
    private static final String BUTTON = "start";
    
    private static final String AUTHZ = "authz";
    private static final String REQUEST = "req";
    
    static final String THIS_SERVLET    = "walletuitest";
 
    private static final String INIT_TAG  = "init";
    private static final String ABORT_TAG = "abort";
    private static final String PARAM_TAG = "msg";
    private static final String ERROR_TAG = "err";

    static class PaymentType {
        String commonName;
        BigDecimal amount;
        String homePage;
        NonDirectPayments nonDirectPayments;
    }
    
    static LinkedHashMap<String, PaymentType> sampleTests = new LinkedHashMap<>();
    
    static void initMerchant(String typeOfPayment,
                             String commonName, 
                             String homePage, 
                             String amount, 
                             NonDirectPayments nonDirectPayments) {
        PaymentType o = new PaymentType();
        o.commonName = commonName;
        o.homePage = homePage;
        o.amount = new BigDecimal(amount);
        o.nonDirectPayments = nonDirectPayments;
        sampleTests.put(typeOfPayment, o);
    }
    
    static {
         initMerchant("Direct",       "Demo Merchant",
                                      "demomerchant.com",
                                      "550",
                                      null);

         initMerchant("Gas Station",  "Planet Gas",
                                      "planetgas.com",
                                      "200",
                                      NonDirectPayments.GAS_STATION);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (request.getParameter(INIT_TAG) == null) {
            guiGetInit(request, response);
        } else {
            walletGetInit(request, response);
        }
    }

    static void printHtml(HttpServletResponse response, 
                          String javascript, 
                          String bodyscript, 
                          String box) throws IOException, ServletException {
        KeyProviderInitServlet.output(response, 
                                      KeyProviderInitServlet.getHTML(javascript, bodyscript, box));
    }

    private void addPaymentMethod(JSONArrayWriter methodList, 
                                  PaymentMethods paymentMethod) throws IOException {
        methodList.setObject()
            .setString(PAYMENT_METHOD_JSON, paymentMethod.getPaymentMethodUrl())
            .setObject(KEY_HASH_JSON, new JSONObjectWriter()
                .setString(JSONCryptoHelper.ALGORITHM_JSON, 
                           HashAlgorithms.SHA256.getJoseAlgorithmId())
                .setBinary(JSONCryptoHelper.VALUE_JSON,
                           HashAlgorithms.SHA256.digest(new byte[]{9,6})));  // Dummy data
    }
    
    private void returnJson(HttpServletResponse response, JSONObjectWriter json) throws IOException {
        logger.info(json.toString());
        response.setContentType(JSON_CONTENT_TYPE);
        response.setHeader("Pragma", "No-Cache");
        response.setDateHeader("EXPIRES", 0);
        response.getOutputStream().write(json.serializeToBytes(JSONOutputFormats.NORMALIZED));
    }

    private void walletGetInit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        HttpSession session = getSession(request);
        PaymentType paymentData = sampleTests.get((String)session.getAttribute(TYPE));
        JSONObjectWriter requestObject = Messages.PAYMENT_CLIENT_REQUEST.createBaseMessage();
        GregorianCalendar timeStamp = new GregorianCalendar();
        GregorianCalendar expires = TimeUtils.inMinutes(30);
        // Create a payment request
        JSONObjectWriter paymentRequest =
            PaymentRequest.encode(paymentData.commonName, 
                                  paymentData.homePage,
                                  paymentData.amount,
                                  Currencies.EUR,
                                  paymentData.nonDirectPayments,
                                  "754329",
                                  timeStamp,
                                  expires);
        JSONArrayWriter methodList = requestObject.setArray(SUPPORTED_PAYMENT_METHODS_JSON);
        addPaymentMethod(methodList, PaymentMethods.BANK_DIRECT);
        addPaymentMethod(methodList, PaymentMethods.SUPER_CARD);
        requestObject.setObject(PAYMENT_REQUEST_JSON, paymentRequest);
        session.setAttribute(REQUEST, requestObject);
        returnJson(response, requestObject);
    }


    private String testAlternatives() {
        boolean first = true;
        StringBuilder html = new StringBuilder("<select id=\"" + TYPE + "\">");
        for (String merchant : sampleTests.keySet()) {
             html.append("<option value=\"")
                .append(merchant)
                .append("\"")
                .append(first ? " selected>" : ">")
                .append(merchant)
                .append("</option>");
             first = false;
        }
        return html.append("</select>").toString();
    }
    

    private String decryptionKey() {
        return new StringBuilder(
                "<textarea" +
                " rows=\"10\" maxlength=\"100000\"" +
                " style=\"box-sizing:border-box;width:100%;white-space:nowrap;overflow:scroll;" +
                "border-width:1px;border-style:solid;border-color:grey;padding:10pt\" " +
                "id=\"" + KEY + "\">" + 
                "-----BEGIN PRIVATE KEY-----\n")
            .append(new Base64().getBase64StringFromBinary(
                    KeyProviderService.keyManagementKey.getPrivateKey().getEncoded()))
            .append(
                "\n-----END PRIVATE KEY-----" +
                "</textarea>").toString();
    }

    private void guiGetInit(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        StringBuilder html = new StringBuilder(
            "<div style=\"padding:0 1em\">" +
                    "This application is intended for testing the Saturn wallet." +
            "</div>" +
            "<div style=\"display:flex;justify-content:center;padding-top:1em\">" +
              "<div>" +
                "<div style=\"margin-bottom:2pt\">Select payment type:</div>" + 
                testAlternatives() +
              "</div>" +
            "</div>" +
            "<div style=\"flex:none;display:block;width:inherit\">" +
              "<div style=\"padding:1em\">" +
                "<div style=\"margin-bottom:2pt\">Bank decryption key:</div>" + 
                 decryptionKey() + 
              "</div>" +
            "</div>" +
            "<div style=\"display:flex;justify-content:center\">" +
              "<div id=\"" + BUTTON + "\" class=\"stdbtn\" onclick=\"invokeWallet()\">" +
                "Invoke Wallet!" + 
              "</div>" +
            "</div>");
        HttpSession session = request.getSession(false);
        if (session != null) {
            byte[] jsonBlob = (byte[])session.getAttribute(AUTHZ);
            if (jsonBlob != null) {
                html.append("<div style=\"padding-top:2em\">Scroll up for the result</div></div><div style=\"padding:1em\">");
                fancyPrint(html, "Wallet Request", new JSONObjectReader((JSONObjectWriter)session.getAttribute(REQUEST)));
                JSONObjectReader walletResponse = JSONParser.parse(jsonBlob);
                fancyPrint(html, "Wallet Response", walletResponse);
                JSONDecryptionDecoder decoder =
                    walletResponse.getObject(ENCRYPTED_AUTHORIZATION_JSON)
                        .getEncryptionObject(new JSONCryptoHelper.Options()
                             .setPublicKeyOption(JSONCryptoHelper.PUBLIC_KEY_OPTIONS.KEY_ID_OR_PUBLIC_KEY)
                             .setKeyIdOption(JSONCryptoHelper.KEY_ID_OPTIONS.OPTIONAL));
                try {
                    KeyPair keyPair = (KeyPair)session.getAttribute(KEY);
                    if (!decoder.getPublicKey().equals(keyPair.getPublic())) {
                        throw new IOException("Non-matching public key");
                    }
                    JSONObjectReader userAuthorization = JSONParser.parse(
                            decoder.getDecryptedData(keyPair.getPrivate()));
                    fancyPrint(html, "User Attestation", userAuthorization);
                    JSONObjectReader signatureObject = 
                            userAuthorization.getObject(AUTHORIZATION_SIGNATURE_JSON);
                    JSONCryptoHelper.Options options = new JSONCryptoHelper.Options();
                    boolean verifiableSignature = 
                            signatureObject.hasProperty(JSONCryptoHelper.PUBLIC_KEY_JSON);
                    options.setPublicKeyOption(verifiableSignature ?
                                            JSONCryptoHelper.PUBLIC_KEY_OPTIONS.REQUIRED
                                                                   :
                                            JSONCryptoHelper.PUBLIC_KEY_OPTIONS.FORBIDDEN);
                     options.setKeyIdOption(verifiableSignature ?
                            JSONCryptoHelper.KEY_ID_OPTIONS.FORBIDDEN
                                                   :
                            JSONCryptoHelper.KEY_ID_OPTIONS.REQUIRED);
                    AuthorizationData authorizationData =
                            new AuthorizationData(userAuthorization, options);
                    if (!verifiableSignature) {
                        html.append("<div>Signature was not verified (no public key)</div>");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    html.append("<div>")
                        .append(e.getMessage())
                        .append("</div>");
                }
            }
        }
        printHtml(response,
                  "history.pushState(null, null, '" + THIS_SERVLET + "');\n" +
                  "window.addEventListener('popstate', function(event) {\n" +
                  "  history.pushState(null, null, '" + THIS_SERVLET + "');\n" +
                  "});\n" +
                  "function applicationError(msg) {\n" +
                  "  console.error(msg);\n" +
                  "  document.getElementById('" + BUTTON + 
                  "').outerHTML = '<div style=\"color:red;font-weight:bold\">' + " +
                    "msg + '</div>';\n" +
                  "}\n\n" +

                  "async function invokeWallet() {\n" +
                  "  if (window.PaymentRequest) {\n" +
                  // It takes a second or two to get PaymentRequest up and running.
                  // Show that to the user.
                  "    document.getElementById('" + BUTTON + "').outerHTML = " +
                    "'<img id=\"" + BUTTON + "\" src=\"waiting.gif\">';\n" +
                  // This code may seem strange but the Web application does not create
                  // an HttpSession so we do this immediately after the user hit the
                  // invokeWallet button.  Using fetch this becomes invisible UI wise.
                  // Read current FORM data as well and add that to the HttpSession
                  // to be created on the server.
                  "    var formData = new URLSearchParams();\n" +
                  "    formData.append('" + TYPE +
                    "', document.getElementById('" + TYPE + "').value);\n" +
                    "    formData.append('" + KEY +
                    "', document.getElementById('" + KEY + "').value);\n" +
                  "    try {\n" +
                  "      const httpResponse = await fetch('" + THIS_SERVLET + "', {\n" +
                  "        method: 'POST',\n" +
                  "        body: formData\n" +
                  "      });\n" +
                  "      if (httpResponse.status == " + HttpServletResponse.SC_OK + ") {\n" +
                  "        const invocationUrl = await httpResponse.text();\n" +
                  // Success! Now we hook into the W3C PaymentRequest using "dummy" payment data
                  "        const details = {total:{label:'total',amount:{currency:'USD',value:'1.00'}}};\n" +
                  "        const supportedInstruments = [{\n" +
                  "          supportedMethods: '" + KeyProviderService.w3cPaymentRequestUrl + "',\n" +
            // Test data
//                  "          supportedMethods: 'weird-pay',\n" +
                  "          data: {url: invocationUrl}\n" +
            // Test data
//                  "          supportedMethods: 'basic-card',\n" +
//                  "          data: {supportedNetworks: ['visa', 'mastercard']}\n" +
                  "        }];\n" +
                  "        const payRequest = new PaymentRequest(supportedInstruments, details);\n" +
                  "        if (await payRequest.canMakePayment()) {\n" +
                  "          const payResponse = await payRequest.show();\n" +
                  "          payResponse.complete('success');\n" +
                  // Note that success does not necessarily mean that the enrollment succeeded,
                  // it just means that the result is a URL to be redirected to.
                  "          document.location.href = payResponse.details." +
                    MobileProxyParameters.W3CPAY_GOTO_URL + ";\n" +
                  "        } else {\n" +
                  "          applicationError('App does not seem to be installed');\n" +
                  "        }\n" +
                  "      } else {\n" +
                  " console.error(httpResponse.status);\n" +
                  "        applicationError(httpResponse.status == " +
                    HttpServletResponse.SC_BAD_REQUEST + 
                    " ? await httpResponse.text() : 'Server error, try again');\n" +
                  "      }\n" +
                  "    } catch (err) {\n" +
                  "      console.error(err);\n" +
                  "      applicationError(err.message);\n" +
                  "    }\n" +
                  "  } else {\n" +
                  // The browser does not support PaymentRequest
                  "      applicationError('Browser does not support PaymentRequest!');\n" +
                  "  }\n" +
                  "}",
                  null,
                  html.toString());
    }
    
    private void fancyPrint(StringBuilder html, String header, JSONObjectReader json) throws IOException {
        html.append("<div style=\"text-align:center\">")
            .append(header)
            .append("</div><div style=\"margin:2pt 0;max-width:100%;white-space:nowrap;overflow:scroll\">")
            .append(json.serializeToString(JSONOutputFormats.PRETTY_HTML))
            .append("</div>");
    }

    private HttpSession getSession(HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IOException("Session not found, timeout?");
        }
        return session;
    }

    String getParameter(HttpServletRequest request, String name) throws IOException {
        String value = request.getParameter(name);
        if (value == null) {
            throw new IOException("Missing: " + name);
        }
        return value;
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (request.getParameter(TYPE) == null) {
            // Return from Wallet
            HttpSession session = getSession(request);
            if (!request.getContentType().equals(JSON_CONTENT_TYPE)) {
                throw new IOException("Bad content type: " + request.getContentType());
            }
            session.setAttribute(AUTHZ, ServletUtil.getData(request));
            response.sendRedirect(THIS_SERVLET);
        } else {
            // Here the real action begins...
            HttpSession session = request.getSession(true);
            session.setAttribute(TYPE, getParameter(request, TYPE));
            String key = getParameter(request, KEY).trim();
            KeyPair keyPair;
            try {
                keyPair = key.startsWith("{") ?
                    JSONParser.parse(key).getKeyPair()
                                              :
                    PEMDecoder.getKeyPair(key.getBytes("utf-8"));
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                KeyProviderInitServlet.output(response, "Suppled key problem: " + e.getMessage());
                return;
            }
            session.setAttribute(KEY, keyPair);
            logger.info(new JSONObjectWriter().setPublicKey(keyPair.getPublic()).toString());
            String url = KeyProviderService.keygen2RunUrl.substring(0, 
                    KeyProviderService.keygen2RunUrl.lastIndexOf('/')) + '/' + THIS_SERVLET;
            String urlEncoded = URLEncoder.encode(url, "utf-8");
            String invocationUrl = MobileProxyParameters.SCHEME_W3CPAY +
                "://" + MobileProxyParameters.HOST_SATURN + 
                "?" + MobileProxyParameters.PUP_COOKIE     + "=" + "JSESSIONID%3D" + session.getId() +
                "&" + MobileProxyParameters.PUP_INIT_URL   + "=" + urlEncoded + "%3F" + INIT_TAG + "%3Dtrue" +
                "&" + MobileProxyParameters.PUP_MAIN_URL   + "=" + urlEncoded +
                "&" + MobileProxyParameters.PUP_CANCEL_URL + "=" + urlEncoded + "%3F" + ABORT_TAG + "%3Dtrue" +
                "&" + MobileProxyParameters.PUP_VERSIONS   + "=" + KeyProviderService.grantedVersions;
            logger.info("POST return=" + invocationUrl);
            KeyProviderInitServlet.output(response, invocationUrl);
        }
    }
}
