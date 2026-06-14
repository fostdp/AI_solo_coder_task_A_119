package com.metrology.balance.repository;

import com.metrology.balance.entity.Dynasty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DynastyRepository extends JpaRepository<Dynasty, Integer> {

    Optional<Dynasty> findByName(String name);

    boolean existsByName(String name);
}
