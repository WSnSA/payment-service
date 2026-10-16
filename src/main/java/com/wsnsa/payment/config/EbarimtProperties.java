package com.wsnsa.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * И-баримтын нийтлэг (merchant-аас үл хамаарах) тохиргоо ({@code ebarimt.*}).
 * Merchant бүрийн PosAPI хаяг, ТТД зэрэг нь merchant дээр хадгалагдана.
 *
 * @param infoApiUrl  ebarimt.mn мэдээллийн API (ТТД, салбарын мэдээлэл).
 * @param easyRegister Easy Register (иргэний ebarimt бүртгэл шалгах) тохиргоо. Хоосон бол шалгалт хийгдэхгүй.
 * @param sendCron    PosAPI-ийн sendData-г өдөр бүр дуудах cron.
 */
@ConfigurationProperties(prefix = "ebarimt")
public record EbarimtProperties(
        String infoApiUrl,
        EasyRegister easyRegister,
        String sendCron
) {
    public EbarimtProperties {
        if (infoApiUrl == null) infoApiUrl = "https://api.ebarimt.mn";
        if (easyRegister == null) easyRegister = new EasyRegister(null, null, null, null, null);
        if (sendCron == null) sendCron = "0 0 23 * * *";
    }

    public record EasyRegister(String authUrl, String apiUrl, String clientId, String username, String password) {
        public boolean configured() {
            return notBlank(authUrl) && notBlank(apiUrl) && notBlank(clientId) && notBlank(username) && notBlank(password);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
