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
package org.webpki.saturn.common;

import java.io.IOException;

import java.security.PublicKey;

import java.security.interfaces.RSAPublicKey;

import java.util.Date;
import java.util.GregorianCalendar;

import org.webpki.crypto.AlgorithmPreferences;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONSignatureDecoder;
import org.webpki.json.JSONSignatureTypes;

import org.webpki.json.encryption.DataEncryptionAlgorithms;
import org.webpki.json.encryption.KeyEncryptionAlgorithms;

public class ProviderAuthority implements BaseProperties {

    public static final String HTTP_VERSION_SUPPORT = "HTTP/1.1";

    private static void test(String authorizationUrl, String transactionUrl) throws IOException {
        if (authorizationUrl == null && transactionUrl == null) {
            throw new IOException("At least one of \"" + AUTHORIZATION_URL_JSON + "\" and \"" +
                                  TRANSACTION_URL_JSON + "\" must be defined");
        }
    }
    
    public static JSONObjectWriter encode(String authorityUrl,
                                          String authorizationUrl,
                                          String transactionUrl,
                                          String[] providerAccountTypes,
                                          PublicKey encryptionPublicKey,
                                          Date expires,
                                          ServerX509Signer signer) throws IOException {
        test(authorizationUrl, transactionUrl);
        JSONObjectWriter wr = Messages.createBaseMessage(Messages.PROVIDER_AUTHORITY)
            .setString(HTTP_VERSION_JSON, HTTP_VERSION_SUPPORT)
            .setString(AUTHORITY_URL_JSON, authorityUrl);
        
        if (authorizationUrl != null) {
            wr.setString(AUTHORIZATION_URL_JSON, authorizationUrl);
        }
    
        if (transactionUrl != null) {
            wr.setString(TRANSACTION_URL_JSON, transactionUrl);
        }
        if (providerAccountTypes != null) {
            wr.setStringArray(PROVIDER_ACCOUNT_TYPES_JSON, providerAccountTypes);
        }
        wr.setObject(ENCRYPTION_PARAMETERS_JSON, new JSONObjectWriter()
            .setString(BaseProperties.DATA_ENCRYPTION_ALGORITHM_JSON, DataEncryptionAlgorithms.JOSE_A128CBC_HS256_ALG_ID.toString())
            .setString(BaseProperties.KEY_ENCRYPTION_ALGORITHM_JSON, 
                         (encryptionPublicKey instanceof RSAPublicKey ?
           KeyEncryptionAlgorithms.JOSE_RSA_OAEP_256_ALG_ID : KeyEncryptionAlgorithms.JOSE_ECDH_ES_ALG_ID).toString())
            .setPublicKey(encryptionPublicKey, AlgorithmPreferences.JOSE))
            .setDateTime(TIME_STAMP_JSON, new Date(), true)
            .setDateTime(BaseProperties.EXPIRES_JSON, expires, true)
            .setSignature(signer);
        return wr;
    }

    public ProviderAuthority(JSONObjectReader rd, String expectedAuthorityUrl) throws IOException {
        Messages.parseBaseMessage(Messages.PROVIDER_AUTHORITY, root = rd);
        httpVersion = rd.getString(HTTP_VERSION_JSON);
        if (!httpVersion.equals(HTTP_VERSION_SUPPORT)) {
            throw new IOException("\"" + HTTP_VERSION_JSON + "\" is currently limited to " + HTTP_VERSION_SUPPORT);
        }
        authorityUrl = rd.getString(AUTHORITY_URL_JSON);
        if (!authorityUrl.equals(expectedAuthorityUrl)) {
            throw new IOException("\"" + AUTHORITY_URL_JSON + "\" mismatch, read=" + authorityUrl +
                                  " expected=" + expectedAuthorityUrl);
        }
        authorizationUrl = rd.getStringConditional(AUTHORIZATION_URL_JSON);
        transactionUrl = rd.getStringConditional(TRANSACTION_URL_JSON);
        test(authorizationUrl, transactionUrl);
        providerAccountTypes = rd.getStringArrayConditional(PROVIDER_ACCOUNT_TYPES_JSON);
        JSONObjectReader encryptionParameters = rd.getObject(ENCRYPTION_PARAMETERS_JSON);
        dataEncryptionAlgorithm = DataEncryptionAlgorithms
            .getAlgorithmFromString(encryptionParameters.getString(DATA_ENCRYPTION_ALGORITHM_JSON));
        keyEncryptionAlgorithm = KeyEncryptionAlgorithms
            .getAlgorithmFromString(encryptionParameters.getString(KEY_ENCRYPTION_ALGORITHM_JSON));
        encryptionPublicKey = encryptionParameters.getPublicKey(AlgorithmPreferences.JOSE);
        timeStamp = rd.getDateTime(TIME_STAMP_JSON);
        expires = rd.getDateTime(EXPIRES_JSON);
        signatureDecoder = rd.getSignature(AlgorithmPreferences.JOSE);
        signatureDecoder.verify(JSONSignatureTypes.X509_CERTIFICATE);
        rd.checkForUnread();
    }

    String httpVersion;
    public String getHttpVersion() {
        return httpVersion;
    }

    String authorityUrl;
    public String getAuthorityUrl() {
        return authorityUrl;
    }

    String authorizationUrl;
    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    String transactionUrl;
    public String getTransactionUrl() {
        return transactionUrl;
    }

    String[] providerAccountTypes;
    public String[] getProviderAccountTypes() {
        return providerAccountTypes;
    }

    DataEncryptionAlgorithms dataEncryptionAlgorithm;
    public DataEncryptionAlgorithms getDataEncryptionAlgorithm() {
        return dataEncryptionAlgorithm;
    }

    KeyEncryptionAlgorithms keyEncryptionAlgorithm;
    public KeyEncryptionAlgorithms getKeyEncryptionAlgorithm() {
        return keyEncryptionAlgorithm;
    }

    PublicKey encryptionPublicKey;
    public PublicKey getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    GregorianCalendar expires;
    public GregorianCalendar getExpires() {
        return expires;
    }

    GregorianCalendar timeStamp;
     public GregorianCalendar getTimeStamp() {
        return timeStamp;
    }

    JSONSignatureDecoder signatureDecoder;
    public JSONSignatureDecoder getSignatureDecoder() {
        return signatureDecoder;
    }

    JSONObjectReader root;
    public JSONObjectReader getRoot() {
        return root;
    }
}