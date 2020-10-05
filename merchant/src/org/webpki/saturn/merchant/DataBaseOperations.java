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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;

import java.util.logging.Logger;

import org.webpki.json.JSONOutputFormats;

import org.webpki.saturn.common.Barcode;
import org.webpki.saturn.common.LineItem;
import org.webpki.saturn.common.ProviderResponseDecoder;
import org.webpki.saturn.common.ReceiptDecoder;
import org.webpki.saturn.common.ReceiptEncoder;
import org.webpki.saturn.common.ServerAsymKeySigner;
import org.webpki.saturn.common.ShippingRecord;
import org.webpki.saturn.common.TaxRecord;

public class DataBaseOperations {

    static Logger logger = Logger.getLogger(DataBaseOperations.class.getCanonicalName());
    
    static void testConnection() throws SQLException {
        try (Connection connection = MerchantService.jdbcDataSource.getConnection();) { }
    }
    
    static synchronized String createOrderId(String random) throws IOException {
        try {
            try (Connection connection = MerchantService.jdbcDataSource.getConnection();
                 CallableStatement stmt = connection.prepareCall("{call CreateOrderIdSP(?,?)}");) {
                stmt.registerOutParameter(1, java.sql.Types.CHAR);
                stmt.setString(2, random);
                stmt.execute();
                return stmt.getString(1);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    static class OrderInfo {
        ReceiptDecoder.Status status;
        String pathData;
    }

    static final String ORDER_STATUS_SQL = "SELECT Status, ReceiptPathData FROM ORDERS WHERE Id=?";

    static OrderInfo getOrderStatus(String orderId) 
    throws SQLException, IOException {
        try (Connection connection = MerchantService.jdbcDataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(ORDER_STATUS_SQL);) {
            stmt.setString(1, orderId);
            try (ResultSet rs = stmt.executeQuery();) {
                if (rs.next()) {
                    OrderInfo orderInfo = new OrderInfo();
                    orderInfo.status = ReceiptDecoder.Status.valueOf(rs.getString(1));
                    orderInfo.pathData = rs.getString(2);
                    return orderInfo;
                }
                return null;
            }
        }
    }

/*
        CREATE PROCEDURE SaveTransactionSP (IN p_Id CHAR(16) CHARACTER SET latin1,
                                            IN p_JsonData TEXT)
*/

    static void saveTransaction(ResultData resultData,
                                MerchantDescriptor merchant,
                                ServerAsymKeySigner signer) throws IOException {
        try {
            ProviderResponseDecoder authorization = resultData.authorization;
            String orderId = authorization.getPayeeReferenceId();
            ArrayList<LineItem> lineItems = new ArrayList<>();
            SavedShoppingCart savedShoppingCart = resultData.walletRequest.savedShoppingCart;
            for (String sku : savedShoppingCart.items.keySet()) {
                ProductEntry productEntry = savedShoppingCart.products.get(sku);
                BigDecimal quantity = savedShoppingCart.items.get(sku);
                lineItems.add(new LineItem(null,
                              productEntry.getDescription(),
                              quantity,
                              productEntry.getOptionalSubtotal(quantity))
                        .setUnit(productEntry.getOptionalUnit())
                        .setPrice(productEntry.getOptionalPrice()));
            }
            BigDecimal optionalSubtotal = null;
            TaxRecord optionalTaxRecord = null;
            if (savedShoppingCart.subtotal != 0) {
                optionalSubtotal = new BigDecimal(BigInteger.valueOf(savedShoppingCart.subtotal), 2);
                optionalTaxRecord = new TaxRecord(
                        new BigDecimal(BigInteger.valueOf(savedShoppingCart.tax), 2),
                        BigDecimal.TEN);
            }
            ShippingRecord optionalShippingRecord = null;
            if (savedShoppingCart.products == SpaceProducts.products) {
                optionalShippingRecord = new ShippingRecord("Free shipping", new BigDecimal("0.00"));
            }

            ReceiptEncoder receiptEncoder = new ReceiptEncoder(
                    orderId,
                    authorization.getPayeeTimeStamp(),
                    merchant.commonName,
                    merchant.optionalPhysicalAddress,
                    merchant.optionalPhoneNumber,
                    merchant.optionalEmailAddress,
                    authorization.getAmount(),
                    authorization.getCurrency(),
                    optionalShippingRecord,
                    optionalSubtotal,
                    (BigDecimal) null,
                    optionalTaxRecord,
                    lineItems,
                    new Barcode(orderId, Barcode.BarcodeTypes.CODE_128),
                    "Free text...",
                    authorization.getPaymentMethodName(),
                    authorization.getAccountReference(),
                    authorization.getPayeeAuthorityUrl(),
                    resultData.providerCommonName,
                    resultData.providerAuthorityUrl,
                    authorization.getProviderReferenceId(),
                    authorization.getPayeeRequestId(),
                    authorization.getProviderTimeStamp(),
                    signer);
            try (Connection connection = MerchantService.jdbcDataSource.getConnection();
                 CallableStatement stmt = 
                         connection.prepareCall("{call SaveTransactionSP(?,?)}");) {
                stmt.setString(1, orderId);

                stmt.setBytes(2, receiptEncoder.getReceiptDocument()
                                                 .serializeToBytes(JSONOutputFormats.NORMALIZED));
                stmt.execute();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    static final String RECEIPT_FETCH_CORE_SQL = "SELECT JsonData FROM RECEIPTS WHERE Id=?";

    static byte[] getReceiptData(String orderId) 
    throws IOException, SQLException {
        try (Connection connection = MerchantService.jdbcDataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(RECEIPT_FETCH_CORE_SQL);) {
            stmt.setString(1, orderId);
            try (ResultSet rs = stmt.executeQuery();) {
                if (!rs.next()) {
                    throw new IOException("Missing core data");
                }
                return rs.getBytes(1);
            }
        }
    }
}
