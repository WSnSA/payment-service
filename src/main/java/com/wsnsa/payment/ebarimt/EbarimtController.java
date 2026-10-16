package com.wsnsa.payment.ebarimt;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.merchant.Merchant;
import com.wsnsa.payment.merchant.MerchantPrincipal;
import com.wsnsa.payment.merchant.MerchantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/ebarimt")
@RequiredArgsConstructor
@Tag(name = "Receipts (И-баримт)")
@SecurityRequirement(name = "apiKey")
public class EbarimtController {

    private final MerchantRepository merchants;
    private final PosApiClient posApi;
    private final EbarimtInfoClient info;

    @Operation(summary = "PosAPI-ийн төлөв (үлдсэн сугалаа, сүүлд илгээсэн огноо)")
    @GetMapping("/posapi/info")
    public JsonNode posInfo(@AuthenticationPrincipal MerchantPrincipal merchant) {
        return posApi.info(merchant(merchant).getEbarimt());
    }

    @Operation(summary = "PosAPI-ийн баримтуудыг татварын систем рүү одоо илгээх")
    @PostMapping("/posapi/send")
    public ResponseEntity<Void> send(@AuthenticationPrincipal MerchantPrincipal merchant) {
        posApi.sendData(merchant(merchant).getEbarimt());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Регистрийн дугаараар ТТД болон байгууллагын мэдээлэл")
    @GetMapping("/taxpayers/{regNo}")
    public Map<String, JsonNode> taxpayer(@PathVariable @Pattern(regexp = "[\\p{L}0-9]{7,14}") String regNo) {
        JsonNode tin = info.tinInfo(regNo);
        String tinValue = tin.path("data").asText(null);
        JsonNode details = tinValue == null || tinValue.isBlank() ? null : info.taxpayerInfo(tinValue);
        return details == null ? Map.of("tin", tin) : Map.of("tin", tin, "info", details);
    }

    @Operation(summary = "Иргэний ebarimt дугаар (consumerNo) хүчинтэй эсэх")
    @GetMapping("/consumers/{consumerNo}")
    public Map<String, Boolean> consumer(@PathVariable @Pattern(regexp = "\\d{8}") String consumerNo) {
        return Map.of("valid", info.isValidConsumer(consumerNo));
    }

    @Operation(summary = "Аймаг/дүүрэг, хорооны кодын жагсаалт")
    @GetMapping("/branches")
    public JsonNode branches() {
        return info.branches();
    }

    private Merchant merchant(MerchantPrincipal p) {
        return merchants.findById(p.merchantId()).orElseThrow(() -> ApiException.notFound("Merchant"));
    }
}
