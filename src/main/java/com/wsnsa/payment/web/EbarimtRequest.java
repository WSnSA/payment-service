package com.wsnsa.payment.web;

import com.wsnsa.payment.payment.EbarimtReceiverType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param receiverType CITIZEN эсвэл COMPANY
 * @param receiver     Иргэн бол И-баримт апп-д бүртгэлтэй утасны дугаар, ААН бол регистрийн дугаар (заавал биш)
 */
public record EbarimtRequest(@NotNull EbarimtReceiverType receiverType, @Size(max = 30) String receiver) {
}
