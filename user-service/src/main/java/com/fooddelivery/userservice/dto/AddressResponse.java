package com.fooddelivery.userservice.dto;

import com.fooddelivery.userservice.model.Address;

public record AddressResponse(Long id, String street, String number, String complement,
                              String neighborhood, String city, String state,
                              String zipCode, boolean main) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(a.getId(), a.getStreet(), a.getNumber(), a.getComplement(),
                a.getNeighborhood(), a.getCity(), a.getState(), a.getZipCode(), a.isMain());
    }
}
