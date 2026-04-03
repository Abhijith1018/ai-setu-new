package com.example.Voxora.Ai.repository;

import com.example.Voxora.Ai.entity.TwilioNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwilioNumberRepository extends JpaRepository<TwilioNumber, Long> {

    Optional<TwilioNumber> findByPhoneNumber(String phoneNumber);
}

