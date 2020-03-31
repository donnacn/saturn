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
import java.io.InputStream;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;

import java.util.LinkedHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;

import org.webpki.crypto.CertificateUtil;
import org.webpki.crypto.CustomCryptoProvider;
import org.webpki.crypto.HashAlgorithms;
import org.webpki.crypto.KeyStoreVerifier;

import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONParser;
import org.webpki.json.JSONX509Verifier;
import org.webpki.json.JSONArrayReader;
import org.webpki.json.JSONDecoderCache;

import org.webpki.util.ArrayUtil;

import org.webpki.saturn.common.AccountDataDecoder;
import org.webpki.saturn.common.AccountDataEncoder;
import org.webpki.saturn.common.BaseProperties;
import org.webpki.saturn.common.PaymentMethods;
import org.webpki.saturn.common.Currencies;
import org.webpki.saturn.common.PayeeCoreProperties;
import org.webpki.saturn.common.ServerAsymKeySigner;
import org.webpki.saturn.common.ExternalCalls;
import org.webpki.saturn.common.CryptoUtils;

import org.webpki.webutil.InitPropertyReader;

public class MerchantService extends InitPropertyReader implements ServletContextListener {

    static Logger logger = Logger.getLogger(MerchantService.class.getCanonicalName());
    
    static final String MERCHANT_BASE_URL            = "merchant_base_url";

    static final String PAYMENT_ROOT                 = "payment_root";
    
    static final String ACQUIRER_ROOT                = "acquirer_root";
    
    static final String PAYEE_PROVIDER_AUTHORITY_URL = "payee_provider_authority_url";

    static final String PAYEE_ACQUIRER_AUTHORITY_URL = "payee_acquirer_authority_url";

    static final String NO_MATCHING_METHODS_URL      = "no_matching_methods_url";

    static final String SERVER_PORT_MAP              = "server_port_map";
    
    static final String DESKTOP_WALLET               = "desktop_wallet";

    static final String CURRENCY                     = "currency";

    static final String ADD_UNUSUAL_CARD             = "add_unusual_card";

    static final String SLOW_OPERATION               = "slow_operation";

    static final String W2NB_WALLET                  = "w2nb_wallet";

    static final String USE_W3C_PAYMENT_REQUEST      = "use_w3c_payment_request";

    static final String W3C_PAYMENT_REQUEST_HOST     = "w3c_payment_request_host";

    static final String MERCHANTS                    = "merchants.json";

    static final String VERSION_CHECK                = "android_webpki_versions";

    static final String BOUNCYCASTLE_FIRST           = "bouncycastle_first";
    
    static final String LOGGING                      = "logging";

    static final String TEST_MODE                    = "test-mode";
    
    static final String DEMO_MERCHANT_COM            = "demomerchant.com";
    static final String PLANET_GAS_COM               = "planetgas.com";
    
    static JSONX509Verifier paymentRoot;
    
    static JSONX509Verifier acquirerRoot;
    
    static JSONDecoderCache knownBackendAccountTypes = new JSONDecoderCache();

    static String noMatchingMethodsUrl;

    static Currencies currency;
    
    static LinkedHashMap<String,MerchantDescriptor> merchants = new LinkedHashMap<>();

    // Web2Native Bridge constants
    static String w2nbWalletName;
    
    static Boolean testMode;

    static boolean logging;
    
    static boolean useW3cPaymentRequest;
    
    static String w3cPaymentRequestUrl;
    
    static ExternalCalls externalCalls;
    
    private static boolean slowOperation;

    static String grantedVersions;

    static boolean desktopWallet;

    static String merchantBaseUrl;

    static void slowOperationSimulator() throws InterruptedException {
        if (slowOperation) {
            Thread.sleep(5000);
        }
    }

    InputStream getResource(String name) throws IOException {
        return this.getClass().getResourceAsStream(getPropertyString(name));
    }

    JSONObjectReader readJSONFile(String name) throws IOException {
        return JSONParser.parse(
                ArrayUtil.getByteArrayFromInputStream(this.getClass().getResourceAsStream(name)));        
    }

    JSONX509Verifier getRoot(String name) throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load (null, null);
        keyStore.setCertificateEntry ("mykey",
                                      CertificateUtil.getCertificateFromBlob (
                                           ArrayUtil.getByteArrayFromInputStream (getResource(name))));        
        return new JSONX509Verifier(new KeyStoreVerifier(keyStore));
    }
    
    void addMerchant(JSONObjectReader merchant) throws IOException {
        String homePage = merchant.getString(BaseProperties.HOME_PAGE_JSON);
        String commonName = merchant.getString(BaseProperties.COMMON_NAME_JSON);
        LinkedHashMap<String,PaymentMethodDescriptor> pmd = new LinkedHashMap<>();
        JSONArrayReader paymentNetworks = merchant.getArray("paymentNetworks");
        do {
            JSONObjectReader paymentNetwork = paymentNetworks.getObject();
            String paymentMethodUrl = paymentNetwork.getString(BaseProperties.PAYMENT_METHOD_JSON);
            PaymentMethods paymentMethod = PaymentMethods.fromTypeUrl(paymentMethodUrl);
            String authorityUrl = getPropertyString(paymentMethod.isCardPayment() ?
                               PAYEE_ACQUIRER_AUTHORITY_URL : PAYEE_PROVIDER_AUTHORITY_URL) +
                    PayeeCoreProperties.createUrlSafeId(
                            paymentNetwork.getString(BaseProperties.LOCAL_PAYEE_ID_JSON));
            HashAlgorithms keyHashAlgorithm = 
                    CryptoUtils.getHashAlgorithm(paymentNetwork, "keyHashAlgorithm");
            KeyPair keyPair = paymentNetwork.getObject("key").getKeyPair();
            LinkedHashMap<String,AccountDataEncoder> receiveAccounts = new LinkedHashMap<>();
            LinkedHashMap<String,AccountDataEncoder> sourceAccounts = new LinkedHashMap<>();
            JSONArrayReader payeeAccounts = paymentNetwork.getArray("payeeAccounts");
            do {
                AccountDataDecoder decoder = (AccountDataDecoder) knownBackendAccountTypes.parse(
                        payeeAccounts.getObject());
                receiveAccounts.put(decoder.getContext(), AccountDataEncoder.create(decoder, true));
                // We make it simple here and refund from the same account we receive to
                sourceAccounts.put(decoder.getContext(), AccountDataEncoder.create(decoder, false));
            } while (payeeAccounts.hasMore());
            pmd.put(paymentMethodUrl,
                    new PaymentMethodDescriptor(authorityUrl,
                                                new ServerAsymKeySigner(keyPair),
                                                keyHashAlgorithm,
                                                CryptoUtils.getJwkThumbPrint(keyPair.getPublic(),
                                                                             keyHashAlgorithm), 
                                                receiveAccounts, 
                                                sourceAccounts));
        } while (paymentNetworks.hasMore());
        merchants.put(homePage, new MerchantDescriptor(homePage, commonName, pmd));
    }

    public static MerchantDescriptor getMerchant(HttpSession session) {
        return merchants.get((String)session.getAttribute(
                MerchantSessionProperties.MERCHANT_HOMEPAGE_ATTR));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        initProperties (sce);
        try {
            CustomCryptoProvider.forcedLoad(getPropertyBoolean(BOUNCYCASTLE_FIRST));

            desktopWallet = getPropertyBoolean(DESKTOP_WALLET);
            
            merchantBaseUrl = getPropertyString(MERCHANT_BASE_URL);

            // The standard payment networks supported by the Saturn demo
            knownBackendAccountTypes.addToCache(org.payments.sepa.SEPAAccountDataDecoder.class);
            knownBackendAccountTypes.addToCache(se.bankgirot.BGAccountDataDecoder.class);

            JSONArrayReader merchantList = readJSONFile(MERCHANTS).getJSONArrayReader();
            do {
                addMerchant(merchantList.getObject());
            } while (merchantList.hasMore());
            if (!merchants.containsKey(DEMO_MERCHANT_COM) ||
                !merchants.containsKey(PLANET_GAS_COM)) {
                throw new IOException("Merchant id errors");
            }

            paymentRoot = getRoot(PAYMENT_ROOT);

            acquirerRoot = getRoot(ACQUIRER_ROOT);
        
            currency = Currencies.valueOf(getPropertyString(CURRENCY));

            w2nbWalletName = getPropertyString(W2NB_WALLET);

            useW3cPaymentRequest = getPropertyBoolean(USE_W3C_PAYMENT_REQUEST);

            w3cPaymentRequestUrl = getPropertyString(W3C_PAYMENT_REQUEST_HOST) + "/method";

            String noMatching = getPropertyString(NO_MATCHING_METHODS_URL);
            if (noMatching.length() != 0) {
                noMatchingMethodsUrl = noMatching;
            }
            
            if (getPropertyString(TEST_MODE).length () > 0) {
                testMode = getPropertyBoolean(TEST_MODE);
            }

            logging = getPropertyBoolean(LOGGING);

            slowOperation = getPropertyBoolean(SLOW_OPERATION);
            
            externalCalls = new ExternalCalls(logging, 
                                              logger,
                                              getPropertyString(SERVER_PORT_MAP).length () > 0 ?
                                                               getPropertyInt(SERVER_PORT_MAP) : null);

            ////////////////////////////////////////////////////////////////////////////////////////////
            // Android WebPKI version check
            ////////////////////////////////////////////////////////////////////////////////////////////
            grantedVersions = getPropertyString(VERSION_CHECK);

            logger.info("Saturn Merchant-server initiated");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "********\n" + e.getMessage() + "\n********", e);
        }
    }
}
