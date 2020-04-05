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
package org.webpki.saturn.common;

import java.io.IOException;

import java.math.BigDecimal;

import java.util.GregorianCalendar;

import org.webpki.json.JSONCryptoHelper;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONSignatureDecoder;
import org.webpki.json.JSONX509Verifier;

import org.webpki.util.ISODateTime;

public class TransactionRequest implements BaseProperties {
    
    public TransactionRequest(JSONObjectReader rd, Boolean cardNetwork) throws IOException {
        root = Messages.TRANSACTION_REQUEST.parseBaseMessage(rd);
        authorizationResponse = new AuthorizationResponse(Messages.AUTHORIZATION_RESPONSE.getEmbeddedMessage(rd));
        recepientUrl = rd.getString(RECEPIENT_URL_JSON);
        actualAmount = rd.getMoney(AMOUNT_JSON,
                                   authorizationResponse.authorizationRequest.paymentRequest.currency.decimals);
        referenceId = rd.getString(REFERENCE_ID_JSON);
        timeStamp = rd.getDateTime(TIME_STAMP_JSON, ISODateTime.COMPLETE);
        software = new Software(rd);
        signatureDecoder = rd.getSignature(REQUEST_SIGNATURE_JSON,
                new JSONCryptoHelper.Options()
                    .setPublicKeyOption(JSONCryptoHelper.PUBLIC_KEY_OPTIONS.REQUIRED)
                    .setKeyIdOption(JSONCryptoHelper.KEY_ID_OPTIONS.FORBIDDEN));
        if (cardNetwork != null &&
            authorizationResponse.authorizationRequest.paymentMethod.isCardPayment() ^ cardNetwork) {
            throw new IOException("Incompatible payment method: " + 
                authorizationResponse.authorizationRequest.paymentMethod.getPaymentMethodUrl());
        }
        rd.checkForUnread();
    }

    Software software;

    JSONObjectReader root;

    String recepientUrl;
    public String getRecepientUrl() {
        return recepientUrl;
    }

    GregorianCalendar timeStamp;
    public GregorianCalendar getTimeStamp() {
        return timeStamp;
    }

    BigDecimal actualAmount;
    public BigDecimal getAmount() {
        return actualAmount;
    }

    String referenceId;
    public String getReferenceId() {
        return referenceId;
    }

    public boolean getTestMode() {
        return authorizationResponse.authorizationRequest.testMode;
    }

    public PaymentRequestDecoder getPaymentRequest() {
        return authorizationResponse.authorizationRequest.paymentRequest;
    }

    JSONSignatureDecoder signatureDecoder;
    public JSONSignatureDecoder getSignatureDecoder() {
        return signatureDecoder;
    }

    AuthorizationResponse authorizationResponse;
    public AuthorizationResponse getAuthorizationResponse() {
        return authorizationResponse;
    }

    public static JSONObjectWriter encode(AuthorizationResponse authorizationResponse,
                                          String recepientUrl,
                                          BigDecimal actualAmount,
                                          String referenceId,
                                          ServerAsymKeySigner signer) throws IOException {
        return Messages.TRANSACTION_REQUEST.createBaseMessage()
            .setString(RECEPIENT_URL_JSON, recepientUrl)
            .setMoney(AMOUNT_JSON,
                    actualAmount,
                    authorizationResponse.authorizationRequest.paymentRequest.currency.decimals)
            .setString(REFERENCE_ID_JSON, referenceId)
            .setDateTime(TIME_STAMP_JSON, new GregorianCalendar(), ISODateTime.UTC_NO_SUBSECONDS)
            .setObject(SOFTWARE_JSON, Software.encode(PaymentRequestEncoder.SOFTWARE_NAME, 
                                                      PaymentRequestEncoder.SOFTWARE_VERSION))
            .setObject(Messages.AUTHORIZATION_RESPONSE.lowerCamelCase(), authorizationResponse.root)
            .setSignature(REQUEST_SIGNATURE_JSON, signer);
    }

    public void verifyPayerBank(JSONX509Verifier paymentRoot) throws IOException {
        authorizationResponse.signatureDecoder.verify(paymentRoot);
    }
}
