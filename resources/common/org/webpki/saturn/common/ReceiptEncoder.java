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

import org.webpki.json.JSONObjectWriter;

import org.webpki.util.ISODateTime;

public class ReceiptEncoder implements BaseProperties {
    
    public ReceiptEncoder(String payeeReferenceId,
                          GregorianCalendar payeeTimeStamp, 
                          String payeeCommonName,
                          BigDecimal amount,
                          Currencies currency,
                          String paymentMethodName,
                          String optionalAccountReference, 
                          String payeeAuthorityUrl,
                          String providerCommonName,
                          String providerAuthorityUrl,
                          GregorianCalendar providerTimeStamp,
                          String providerReferenceId) throws IOException {
        this(ReceiptDecoder.Status.AVAILABLE);
        receiptDocument
            .setString(REFERENCE_ID_JSON, payeeReferenceId)
            .setDateTime(TIME_STAMP_JSON, payeeTimeStamp, ISODateTime.UTC_NO_SUBSECONDS)
            .setString(COMMON_NAME_JSON, payeeCommonName)
            .setMoney(AMOUNT_JSON, amount, currency.getDecimals())
            .setString(CURRENCY_JSON, currency.toString())
            .setString(PAYMENT_METHOD_NAME_JSON, paymentMethodName)
            .setDynamic((wr) -> optionalAccountReference == null ?
                            wr : wr.setString(ACCOUNT_REFERENCE_JSON,
                                              optionalAccountReference))
            .setString(PAYEE_AUTHORITY_URL_JSON, payeeAuthorityUrl)
            .setObject(PROVIDER_DATA_JSON, new JSONObjectWriter()
                    .setString(COMMON_NAME_JSON, providerCommonName)
                    .setString(PROVIDER_AUTHORITY_URL_JSON, providerAuthorityUrl)
                    .setDateTime(TIME_STAMP_JSON, 
                                 providerTimeStamp, 
                                 ISODateTime.UTC_NO_SUBSECONDS)
                    .setString(REFERENCE_ID_JSON, providerReferenceId));
    }

    public ReceiptEncoder(ReceiptDecoder.Status notAvailableStatus) throws IOException {
        receiptDocument = Messages.RECEIPT.createBaseMessage()
                .setString(STATUS_JSON, notAvailableStatus.toString());
    }

    JSONObjectWriter receiptDocument;
    public JSONObjectWriter getReceiptDocument() {
        return receiptDocument;
    }
}
