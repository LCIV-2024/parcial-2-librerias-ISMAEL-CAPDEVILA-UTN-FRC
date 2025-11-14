package com.example.libreria.repository;

import com.example.libreria.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {


    List<Reservation> findByUserId(Long userId);


    List<Reservation> findByStatus(Reservation.ReservationStatus status);


    List<Reservation> findByBookExternalId(Long bookExternalId);


    @Query("SELECT r FROM Reservation r WHERE r.user.id = :userId AND r.status = 'ACTIVE'")
    List<Reservation> findActiveReservationsByUserId(@Param("userId") Long userId);


    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expectedReturnDate < :currentDate")
    List<Reservation> findOverdueReservations(@Param("currentDate") LocalDate currentDate);


    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expectedReturnDate < CURRENT_DATE")
    List<Reservation> findOverdueReservations();

    @Query("SELECT r FROM Reservation r WHERE r.startDate >= :startDate AND r.startDate <= :endDate")
    List<Reservation> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);


    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.book.externalId = :bookExternalId AND r.status = 'ACTIVE'")
    Long countActiveReservationsByBookExternalId(@Param("bookExternalId") Long bookExternalId);
}