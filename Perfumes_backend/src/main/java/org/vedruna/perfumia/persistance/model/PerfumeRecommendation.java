package org.vedruna.perfumia.persistance.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "perfume_recommendations")
public class PerfumeRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    Integer recommendationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    User user;

    @Column(name = "perfume_name")
    String perfumeName;

    @Column(name = "brand")
    String brand;

    @Column(name = "description")
    String description;

    @Column(name = "season")
    String season;

    @Column(name = "notes")
    String notes;

    @Column(name = "source")
    String source;

    @Column(name = "image_url")
    String imageUrl;

    @Column(name = "accepted")
    Boolean accepted;

    @Column(name = "favorite")
    Boolean favorite = false;

    @Column(name = "rating")
    Integer rating;

    @Column(name = "fragella_rating")
    String fragellaRating;

    @Column(name = "create_date")
    LocalDateTime createDate;
}
