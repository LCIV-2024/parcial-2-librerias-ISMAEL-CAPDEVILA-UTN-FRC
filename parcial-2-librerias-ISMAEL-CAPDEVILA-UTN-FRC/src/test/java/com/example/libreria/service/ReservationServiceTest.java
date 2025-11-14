package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookService bookService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReservationService reservationService;

    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    private ReservationRequestDTO reservationRequestDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");

        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);

        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setLateFee(BigDecimal.ZERO);
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());

        reservationRequestDTO = new ReservationRequestDTO();
        reservationRequestDTO.setUserId(1L);
        reservationRequestDTO.setBookExternalId(258027L);
        reservationRequestDTO.setRentalDays(7);
        reservationRequestDTO.setStartDate(LocalDate.now());
    }

    @Test
    void testCreateReservation_Success() {
        // Arrange
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        doNothing().when(bookService).decreaseAvailableQuantity(anyLong());

        // Act
        ReservationResponseDTO result = reservationService.createReservation(reservationRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
        assertEquals(testUser.getId(), result.getUserId());
        assertEquals(testBook.getExternalId(), result.getBookExternalId());
        assertEquals(7, result.getRentalDays());
        assertEquals(new BigDecimal("111.93"), result.getTotalFee());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.getStatus());

        verify(userService, times(1)).getUserEntity(1L);
        verify(bookRepository, times(1)).findByExternalId(258027L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).decreaseAvailableQuantity(258027L);
    }

    @Test
    void testCreateReservation_BookNotAvailable() {
        // Arrange
        testBook.setAvailableQuantity(0);
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.createReservation(reservationRequestDTO);
        });

        assertTrue(exception.getMessage().contains("No hay copias disponibles"));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(bookService, never()).decreaseAvailableQuantity(anyLong());
    }

    @Test
    void testCreateReservation_UserNotFound() {
        // Arrange
        when(userService.getUserEntity(1L)).thenThrow(new RuntimeException("Usuario no encontrado"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            reservationService.createReservation(reservationRequestDTO);
        });

        verify(bookRepository, never()).findByExternalId(anyLong());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testCreateReservation_BookNotFound() {
        // Arrange
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.createReservation(reservationRequestDTO);
        });

        assertTrue(exception.getMessage().contains("Libro no encontrado"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testReturnBook_OnTime() {
        // Arrange
        LocalDate returnDate = LocalDate.now().plusDays(5); // Devuelto antes del día esperado
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO(returnDate);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setActualReturnDate(returnDate);
            saved.setStatus(Reservation.ReservationStatus.RETURNED);
            return saved;
        });
        doNothing().when(bookService).increaseAvailableQuantity(anyLong());

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getLateFee());

        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
    }

    @Test
    void testReturnBook_Overdue() {
        // Arrange
        LocalDate returnDate = LocalDate.now().plusDays(10); // 3 días de retraso
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO(returnDate);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setActualReturnDate(returnDate);
            saved.setStatus(Reservation.ReservationStatus.OVERDUE);
            // 15% de $15.99 = $2.40 por día, 3 días = $7.20
            saved.setLateFee(new BigDecimal("7.20"));
            return saved;
        });
        doNothing().when(bookService).increaseAvailableQuantity(anyLong());

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.OVERDUE, result.getStatus());
        assertTrue(result.getLateFee().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("7.20"), result.getLateFee());

        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
    }

    @Test
    void testReturnBook_AlreadyReturned() {
        // Arrange
        testReservation.setStatus(Reservation.ReservationStatus.RETURNED);
        testReservation.setActualReturnDate(LocalDate.now().minusDays(1));
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO(LocalDate.now());

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.returnBook(1L, returnRequest);
        });

        assertTrue(exception.getMessage().contains("ya fue devuelta"));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(bookService, never()).increaseAvailableQuantity(anyLong());
    }

    @Test
    void testReturnBook_InvalidReturnDate() {
        // Arrange
        LocalDate invalidReturnDate = LocalDate.now().minusDays(1); // Antes de la fecha de inicio
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO(invalidReturnDate);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.returnBook(1L, returnRequest);
        });

        assertTrue(exception.getMessage().contains("no puede ser anterior"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        ReservationResponseDTO result = reservationService.getReservationById(1L);

        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }

    @Test
    void testGetReservationById_NotFound() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            reservationService.getReservationById(1L);
        });
    }

    @Test
    void testGetAllReservations() {
        Reservation reservation2 = new Reservation();
        reservation2.setId(2L);
        reservation2.setUser(testUser);
        reservation2.setBook(testBook);
        reservation2.setRentalDays(5);
        reservation2.setStartDate(LocalDate.now());
        reservation2.setExpectedReturnDate(LocalDate.now().plusDays(5));
        reservation2.setDailyRate(testBook.getPrice());
        reservation2.setTotalFee(new BigDecimal("79.95"));
        reservation2.setStatus(Reservation.ReservationStatus.ACTIVE);
        reservation2.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, reservation2));

        List<ReservationResponseDTO> result = reservationService.getAllReservations();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));

        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getId(), result.get(0).getUserId());
    }

    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));

        List<ReservationResponseDTO> result = reservationService.getActiveReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void testGetOverdueReservations() {
        testReservation.setExpectedReturnDate(LocalDate.now().minusDays(3));

        when(reservationRepository.findOverdueReservations())
                .thenReturn(Arrays.asList(testReservation));

        List<ReservationResponseDTO> result = reservationService.getOverdueReservations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getExpectedReturnDate().isBefore(LocalDate.now()));
    }
}