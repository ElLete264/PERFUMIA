package org.vedruna.perfumia.persistance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "perfume_profiles")
public class PerfumeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    Integer profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    User user;

    @Column(name = "gender_target")
    String genderTarget;

    @Column(name = "season")
    String season;

    @Column(name = "intensity")
    String intensity;

    @Column(name = "preferred_notes")
    String preferredNotes;

    @Column(name = "disliked_notes")
    String dislikedNotes;

    @Column(name = "occasion")
    String occasion;

    @Column(name = "budget")
    String budget;

    @Column(name = "last_summary")
    String lastSummary;
}
