/*
 *  Copyright 2006-2016 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.supercard;

import java.io.IOException;

import java.util.GregorianCalendar;

import org.webpki.json.JSONObjectReader;

import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.AccountDataDecoder;

public final class SupercardAresDecoder extends AccountDataDecoder {

    private static final long serialVersionUID = 1L;

    String cardNumber;                   // PAN
    String cardHolder;                   // Name
    GregorianCalendar expirationDate;    // Card expiration date
    String securityCode;                 // CCV or similar

    @Override
    protected void readJSONData(JSONObjectReader rd) throws IOException {
        cardNumber = rd.getString(SupercardAresEncoder.CARD_NUMBER_JSON);
        cardHolder = rd.getString(SupercardAresEncoder.CARD_HOLDER_JSON);
        expirationDate = rd.getDateTime(BaseProperties.EXPIRES_JSON);
        securityCode = rd.getString(SupercardAresEncoder.SECURITY_CODE_JSON);
    }

    @Override
    public String getContext() {
        return SupercardAresEncoder.ARES_CONTEXT;
    }
}
