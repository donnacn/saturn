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
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.security.cert.X509Certificate;

import java.net.URLEncoder;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.webpki.crypto.KeyAlgorithms;

import org.webpki.keygen2.ServerState;
import org.webpki.keygen2.KeySpecifier;
import org.webpki.keygen2.KeyGen2URIs;
import org.webpki.keygen2.InvocationResponseDecoder;
import org.webpki.keygen2.ProvisioningInitializationResponseDecoder;
import org.webpki.keygen2.CredentialDiscoveryResponseDecoder;
import org.webpki.keygen2.KeyCreationResponseDecoder;
import org.webpki.keygen2.ProvisioningFinalizationResponseDecoder;
import org.webpki.keygen2.InvocationRequestEncoder;
import org.webpki.keygen2.ProvisioningInitializationRequestEncoder;
import org.webpki.keygen2.CredentialDiscoveryRequestEncoder;
import org.webpki.keygen2.KeyCreationRequestEncoder;
import org.webpki.keygen2.ProvisioningFinalizationRequestEncoder;

import org.webpki.sks.Grouping;
import org.webpki.sks.AppUsage;
import org.webpki.sks.PassphraseFormat;

import org.webpki.saturn.common.AuthorizationData;
import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.CardDataEncoder;
import org.webpki.saturn.common.CardImageData;

import org.webpki.util.MIMETypedObject;

import org.webpki.webutil.ServletUtil;

import org.webpki.json.JSONEncoder;
import org.webpki.json.JSONDecoder;
import org.webpki.json.JSONOutputFormats;

// A KeyGen2 protocol runner that setups pre-configured wallet keys.

public class KeyProviderServlet extends HttpServlet implements BaseProperties {

    private static final long serialVersionUID = 1L;

    static Logger log = Logger.getLogger(KeyProviderServlet.class.getCanonicalName());
    
    static String success_image_and_message;
    
    void returnKeyGen2Error(HttpServletResponse response, String errorMessage) throws IOException, ServletException {
        ////////////////////////////////////////////////////////////////////////////////////////////
        // Server errors are returned as HTTP redirects taking the client out of its KeyGen2 mode
        ////////////////////////////////////////////////////////////////////////////////////////////
        response.sendRedirect(KeyProviderInitServlet.keygen2EnrollmentUrl + 
                              "?" +
                              KeyProviderInitServlet.ERROR_TAG +
                              "=" +
                              URLEncoder.encode(errorMessage, "UTF-8"));
    }
    
    void keygen2JSONBody(HttpServletResponse response, JSONEncoder object) throws IOException {
        byte[] jsonData = object.serializeJSONDocument(JSONOutputFormats.PRETTY_PRINT);
        if (KeyProviderService.logging) {
            log.info("Sent message\n" + new String(jsonData, "UTF-8"));
        }
        response.setContentType(JSON_CONTENT_TYPE);
        response.setHeader("Pragma", "No-Cache");
        response.setDateHeader("EXPIRES", 0);
        response.getOutputStream().write(jsonData);
    }

    void requestKeyGen2KeyCreation(HttpServletResponse response, ServerState keygen2State)
            throws IOException {
        ServerState.PINPolicy standardPinPolicy = 
            keygen2State.createPINPolicy(PassphraseFormat.NUMERIC,
                                         4,
                                         8,
                                         3,
                                         null);
        standardPinPolicy.setGrouping(Grouping.SHARED);
        ServerState.PINPolicy serverPinPolicy = 
                keygen2State.createPINPolicy(PassphraseFormat.STRING,
                                             4,
                                             8,
                                             3,
                                             null);
    
        for (KeyProviderService.CredentialTemplate credentialTemplate 
                              : 
             KeyProviderService.credentialTemplates) {
            ServerState.Key key = credentialTemplate.optionalServerPin == null ?
                keygen2State.createKey(AppUsage.SIGNATURE,
                                       new KeySpecifier(credentialTemplate.keyAlgorithm),
                                       standardPinPolicy) 
                                                                          :
                keygen2State
                    .createKeyWithPresetPIN(AppUsage.SIGNATURE,
                                            new KeySpecifier(credentialTemplate.keyAlgorithm),
                                            serverPinPolicy,
                                            credentialTemplate.optionalServerPin);                           
                key.addEndorsedAlgorithm(credentialTemplate.signatureAlgorithm);
                key.setFriendlyName(credentialTemplate.friendlyName);
/*
            key.setCertificatePath(paymentCredential.dummyCertificatePath);
            key.addExtension(BaseProperties.SATURN_WEB_PAY_CONTEXT_URI,
                             CardDataEncoder.encode(paymentCredential.paymentMethod,
                                                    paymentCredential.accountId, 
                                                    paymentCredential.authorityUrl, 
                                                    paymentCredential.signatureAlgorithm, 
                                                    paymentCredential.dataEncryptionAlgorithm, 
                                                    paymentCredential.keyEncryptionAlgorithm, 
                                                    paymentCredential.encryptionKey,
                                                    null,
                                                    null,
                                                    paymentCredential.tempBalanceFix)
                                                        .serializeToBytes(JSONOutputFormats.NORMALIZED));


            key.addLogotype(KeyGen2URIs.LOGOTYPES.CARD, new MIMETypedObject() {

                @Override
                public byte[] getData() throws IOException {
                    return new String(paymentCredential.svgCardImage)
                        .replace(CardImageData.STANDARD_NAME, paymentCredential.cardHolder)
                        .replace(CardImageData.STANDARD_ACCOUNT, paymentCredential.cardFormatted ?
                            AuthorizationData.formatCardNumber(paymentCredential.accountId) 
                                                                        :
                            paymentCredential.accountId).getBytes("utf-8");
                }

                @Override
                public String getMimeType() throws IOException {
                    return "image/svg+xml";
                }
               
            });
*/
        }
    
        keygen2JSONBody(response, 
                        new KeyCreationRequestEncoder(keygen2State));
      }

    String certificateData(X509Certificate certificate) {
        return ", Subject='" + certificate.getSubjectX500Principal().getName() +
               "', Serial=" + certificate.getSerialNumber();
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
           throws IOException, ServletException {
        executeRequest(request, response, false);
    }

    void executeRequest(HttpServletRequest request, HttpServletResponse response, boolean init)
         throws IOException, ServletException {
        String keygen2EnrollmentUrl = KeyProviderInitServlet.keygen2EnrollmentUrl;
        HttpSession session = request.getSession(false);
        try {
            ////////////////////////////////////////////////////////////////////////////////////////////
            // Check that the request is properly authenticated
            ////////////////////////////////////////////////////////////////////////////////////////////
            if (session == null) {
                returnKeyGen2Error(response, "Session timed out");
                return;
             }
            ServerState keygen2State =
                (ServerState) session.getAttribute(KeyProviderInitServlet.KEYGEN2_SESSION_ATTR);
            if (keygen2State == null) {
                throw new IOException("Server state missing");
            }
            ////////////////////////////////////////////////////////////////////////////////////////////
            // Check if it is the first (trigger) message from the client
            ////////////////////////////////////////////////////////////////////////////////////////////
            if (init) {
                InvocationRequestEncoder invocationRequest = new InvocationRequestEncoder(keygen2State);
                keygen2State.addImageAttributesQuery(KeyGen2URIs.LOGOTYPES.LIST);
                keygen2JSONBody(response, invocationRequest);
                return;
              }

            ////////////////////////////////////////////////////////////////////////////////////////////
            // It should be a genuine KeyGen2 response.  Note that the order is verified!
            ////////////////////////////////////////////////////////////////////////////////////////////
            byte[] jsonData = ServletUtil.getData(request);
            if (!request.getContentType().equals(JSON_CONTENT_TYPE)) {
                throw new IOException("Wrong \"Content-Type\": " + request.getContentType());
            }
            if (KeyProviderService.logging) {
                log.info("Received message:\n" + new String(jsonData, "UTF-8"));
            }
            JSONDecoder jsonObject = KeyProviderService.keygen2JSONCache.parse(jsonData);
            switch (keygen2State.getProtocolPhase()) {
                case INVOCATION:
                    InvocationResponseDecoder invocationResponse = (InvocationResponseDecoder) jsonObject;
                    keygen2State.update(invocationResponse);

                    // Now we really start doing something
                    ProvisioningInitializationRequestEncoder provisioningInitRequest =
                        new ProvisioningInitializationRequestEncoder(keygen2State,
                                                                       (short)1000,
                                                                       (short)50);
                    provisioningInitRequest.setKeyManagementKey(
                            KeyProviderService.keyManagementKey.getPublicKey());
                    keygen2JSONBody(response, provisioningInitRequest);
                    return;

                case PROVISIONING_INITIALIZATION:
                    ProvisioningInitializationResponseDecoder provisioningInitResponse = (ProvisioningInitializationResponseDecoder) jsonObject;
                    keygen2State.update(provisioningInitResponse);

                    log.info("Device Certificate=" +
                            certificateData(keygen2State.getDeviceCertificate()));

                    ////////////////////////////////////////////////////////////////////////
                    // Finding out keys that should be deleted.  We don't want duplicate 
                    // payment credentials 
                    ////////////////////////////////////////////////////////////////////////
                    CredentialDiscoveryRequestEncoder credentialDiscoveryRequest =
                            new CredentialDiscoveryRequestEncoder(keygen2State);
                    credentialDiscoveryRequest.addLookupDescriptor(
                            KeyProviderService.keyManagementKey.getPublicKey());
                    keygen2JSONBody(response, credentialDiscoveryRequest);
                    return;

                case CREDENTIAL_DISCOVERY:
                    CredentialDiscoveryResponseDecoder credentiaDiscoveryResponse =
                        (CredentialDiscoveryResponseDecoder) jsonObject;
                    keygen2State.update(credentiaDiscoveryResponse);

                    ////////////////////////////////////////////////////////////////////////
                    // Mark keys for deletion
                    ////////////////////////////////////////////////////////////////////////
                    for (CredentialDiscoveryResponseDecoder.LookupResult lookupResult 
                           : 
                         credentiaDiscoveryResponse.getLookupResults()) {
                        for (CredentialDiscoveryResponseDecoder
                                .MatchingCredential matchingCredential
                                 : 
                             lookupResult.getMatchingCredentials()) {
                            X509Certificate endEntityCertificate = 
                                    matchingCredential.getCertificatePath()[0];
                            keygen2State
                                .addPostDeleteKey(matchingCredential.getClientSessionId(), 
                                                  matchingCredential.getServerSessionId(),
                                                  endEntityCertificate,
                                                  KeyProviderService.keyManagementKey.getPublicKey());
                          log.info("Deleting key=" + certificateData(endEntityCertificate));
                        }
                    }

                    ////////////////////////////////////////////////////////////////////////
                    // Now order a set of new keys including suitable protection objects
                    ////////////////////////////////////////////////////////////////////////
                    ServerState.PINPolicy standardPinPolicy = 
                            keygen2State.createPINPolicy(PassphraseFormat.NUMERIC,
                                                         4,
                                                         8,
                                                         3,
                                                         null);
                    standardPinPolicy.setGrouping(Grouping.SHARED);
                    ServerState.PINPolicy serverPinPolicy = 
                            keygen2State.createPINPolicy(PassphraseFormat.STRING,
                                                         4,
                                                         8,
                                                         3,
                                                         null);
                  
                    for (KeyProviderService.CredentialTemplate credentialTemplate 
                                        : 
                         KeyProviderService.credentialTemplates) {
                        ServerState.Key key = credentialTemplate.optionalServerPin == null ?
                                keygen2State.createKey(AppUsage.SIGNATURE,
                                                       new KeySpecifier(credentialTemplate
                                                               .keyAlgorithm),
                                                       standardPinPolicy) 
                                              :
                                keygen2State
                                    .createKeyWithPresetPIN(AppUsage.SIGNATURE,
                                                            new KeySpecifier(
                                                          credentialTemplate.keyAlgorithm),
                                                            serverPinPolicy,
                                                            credentialTemplate.optionalServerPin);                           
                        key.addEndorsedAlgorithm(credentialTemplate.signatureAlgorithm);
                        key.setFriendlyName(credentialTemplate.friendlyName);
                        key.setUserObject(credentialTemplate);
                    }
                    keygen2JSONBody(response, new KeyCreationRequestEncoder(keygen2State));
                    return;

                case KEY_CREATION:
                    KeyCreationResponseDecoder keyCreationResponse = 
                        (KeyCreationResponseDecoder) jsonObject;
                    keygen2State.update(keyCreationResponse);

                    ////////////////////////////////////////////////////////////////////////
                    // Keys have been created, now add the data needed in order to make 
                    // them usable in Saturn as well
                    ////////////////////////////////////////////////////////////////////////
                    
                    // However, since this is a demo without KYC and login we need to
                    // first create a user since even demo users are supposed to be
                    // independent of each other.  Note, user name is just an "alias"
                    // so it does NOT function as a user ID...
                    String userName = "Funny Guy";
                    int userId = DataBaseOperations.createUser(userName);
                    for (ServerState.Key key : keygen2State.getKeys()) {
                        KeyProviderService.CredentialTemplate credentialTemplate =
                                (KeyProviderService.CredentialTemplate)key.getUserObject();
                        String credentialId = 
                                DataBaseOperations
                                    .createAccountAndCredential(userId, 
                                                                credentialTemplate
                                                                    .accountType.getIntValue(),
                                                                credentialTemplate.paymentMethod,
                                                                key.getPublicKey(),
                                                                null);
                    }
                    keygen2JSONBody(response, 
                                    new ProvisioningFinalizationRequestEncoder(keygen2State));
                    return;

                case PROVISIONING_FINALIZATION:
                    ProvisioningFinalizationResponseDecoder provisioningFinalResponse =
                        (ProvisioningFinalizationResponseDecoder) jsonObject;
                    keygen2State.update(provisioningFinalResponse);
                    log.info("Successful KeyGen2 run");

                    ////////////////////////////////////////////////////////////////////////
                    // We are done, return an HTTP redirect taking 
                    // the client out of its KeyGen2 mode
                    ////////////////////////////////////////////////////////////////////////
                    response.sendRedirect(keygen2EnrollmentUrl);
                    return;

                default:
                  throw new IOException("Unxepected state");
            }
        } catch (Exception e) {
            if (session != null) {
                session.invalidate();
            }
            log.log(Level.SEVERE, "KeyGen2 failure", e);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter printerWriter = new PrintWriter(baos);
            e.printStackTrace(printerWriter);
            printerWriter.flush();
            returnKeyGen2Error(response, baos.toString("UTF-8"));
        }
    }

    boolean foundData(HttpServletRequest request, StringBuilder result, String tag) {
        String value = request.getParameter(tag);
        if (value == null) {
            return false;
        }
        result.append(value);
        return true;
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
           throws IOException, ServletException {
        if (request.getParameter(KeyProviderInitServlet.INIT_TAG) != null) {
            executeRequest(request, response, true);
            return;
        }
        StringBuilder html = new StringBuilder("<tr><td width=\"100%\" align=\"center\" valign=\"middle\">");
        StringBuilder result = new StringBuilder();
        if (foundData(request, result, KeyProviderInitServlet.ERROR_TAG)) {
            html.append("<table><tr><td><b>Failure Report:</b></td></tr><tr><td><pre><font color=\"red\">")
                .append(result)
                .append("</font></pre></td></tr></table>");
        } else if (foundData(request, result, KeyProviderInitServlet.PARAM_TAG)) {
            html.append(result);
        } else if (foundData(request, result, KeyProviderInitServlet.ABORT_TAG)) {
            log.info("KeyGen2 run aborted by the user");
            html.append("<b>Aborted by the user!</b>");
        } else {
            HttpSession session = request.getSession(false);
            if (session == null) {
                html.append("<b>You need to restart the session</b>");
            } else {
                session.invalidate();
                html.append(KeyProviderInitServlet.successMessage);
            }
        }
        KeyProviderInitServlet.output(response, 
                                      KeyProviderInitServlet.getHTML(
                                              "history.pushState(null, null, 'init');\n" +
                                                      "window.addEventListener('popstate', function(event) {\n" +
                                                      "    history.pushState(null, null, 'init');\n" +
                                                      "});\n"
,
                                                                     null,
                                                                     html.append("</td></tr>").toString()));
    }

}
