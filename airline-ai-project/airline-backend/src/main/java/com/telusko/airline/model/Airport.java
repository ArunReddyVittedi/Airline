package com.telusko.airline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "airport")
@Getter
@Setter
@NoArgsConstructor
public class Airport {

    /**
     * The IATA code is the primary key on purpose. It is stable, it is what passengers type,
     * and it is what the model produces when it says "BOM". A surrogate id would mean a
     * lookup on every single tool call for no benefit.
     */
    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

    public Airport(String code, String city, String name, String country) {
        this.code = code;
        this.city = city;
        this.name = name;
        this.country = country;
    }
}
