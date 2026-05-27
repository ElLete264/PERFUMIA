package org.vedruna.perfumia.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerfumeItem {
    String name;
    String brand;
    String description;
    String notes;
    String season;
    String source;
    String imageUrl;
    String price;
    String longevity;
    String sillage;
    String oilType;
    String fragellaRating;
    String gender;
    String priceValue;
}
