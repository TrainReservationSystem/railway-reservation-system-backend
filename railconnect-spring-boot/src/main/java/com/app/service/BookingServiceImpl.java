package com.app.service;

import java.util.List;
//import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.dao.BookingDAO;
//import com.app.dao.RouteDAO;
import com.app.dao.TicketDAO;
//import com.app.dao.TrainDAO;
import com.app.dto.BookingDTO;
import com.app.dto.TicketDTO;
import com.app.entities.BookingEntity;
import com.app.entities.TicketEntity;


@Service
@Transactional
public class BookingServiceImpl implements BookingService {

	@Autowired
	private BookingDAO bookingDao;

	@Autowired
	private TicketDAO ticketDao;

//	@Autowired
//	private RouteDAO routeDao;
//
//	@Autowired
//	private TrainDAO trainDao;

	@SuppressWarnings("null")
	@Override
	public List<BookingDTO> findUserBookings(Long userId) {
		// find all the ticket info associated with the respective bookings
		List<BookingEntity> userBookings = bookingDao.findByUserId(userId);

		for (BookingEntity booking : userBookings) {
			booking.setTickets(ticketDao.findByBookingPnrNumber(booking.getPnrNumber()));
		}
		List<BookingDTO> userBookingDTOs = null;

		for (int i = 0; i < userBookings.size(); i++) {
			userBookingDTOs.add(convertEntityToDto(userBookings.get(i)));
		}

		return userBookingDTOs;
	}

	public BookingEntity convertDtoToEntity(BookingDTO bookingDTO) {
		BookingEntity bookingEntity = new BookingEntity();
		bookingEntity.setPnrNumber(bookingDTO.getPnrNumber());
		bookingEntity.setCoach(bookingDTO.getCoach());
		bookingEntity.setDateOfJourney(bookingDTO.getDateOfJourney());
		bookingEntity.setFromStation(bookingDTO.getFrom());
		bookingEntity.setToStation(bookingDTO.getTo());
//		bookingEntity.setTrain(new TrainService().getTrainById(bookingDTO.getTrainNumber()));
//		bookingEntity.setUser(new UserServiceImpl().getUserDetailsById(bookingDTO.getUserId()));

		List<TicketEntity> ticketEntities = bookingDTO.getTickets().stream()
				.map(ticketDTO -> TicketServiceImpl.convertDtoToEntity(ticketDTO)).collect(Collectors.toList());

		bookingEntity.setTickets(ticketEntities);

		return bookingEntity;
	}

	// Convert BookingEntity to BookingDTO
	public BookingDTO convertEntityToDto(BookingEntity bookingEntity) {
		BookingDTO bookingDTO = new BookingDTO();
		bookingDTO.setPnrNumber(bookingEntity.getPnrNumber());
		bookingDTO.setCoach(bookingEntity.getCoach());
		bookingDTO.setUserId(bookingEntity.getUser().getId());
		bookingDTO.setTrainNumber(bookingEntity.getTrain().getTrainNumber());
		bookingDTO.setDateOfJourney(bookingEntity.getDateOfJourney());
		bookingDTO.setFrom(bookingEntity.getFromStation());
		bookingDTO.setTo(bookingEntity.getToStation());

		List<TicketDTO> ticketDTOs = bookingEntity.getTickets().stream()
				.map(ticketEntity -> TicketServiceImpl.convertEntityToDto(ticketEntity)) // Assuming this method exists
				.collect(Collectors.toList());
		bookingDTO.setTickets(ticketDTOs); // This should be a Set<TicketDTO>, not Set<TicketEntity>

		return bookingDTO;
	}

	@SuppressWarnings("null")
	@Override
	public List<BookingDTO> showAllBookings() {
		List<BookingDTO> bookingDTOs = null;
		List<BookingEntity> bookingEntities = bookingDao.findAll();
		
		for(int i=0; i< bookingEntities.size(); i++) {
			bookingDTOs.add(convertEntityToDto(bookingEntities.get(i)));
		}
		return bookingDTOs;
	}//Getting all ticket information is not implemented in this method

	@Override
	public BookingDTO addNewBooking(BookingDTO booking) {
		List<TicketDTO> tickets = booking.getTickets();
		for (TicketDTO ticketDTO : tickets) {
			ticketDao.save(TicketServiceImpl.convertDtoToEntity(ticketDTO));
		} // to save the tickets first in the ticket table 
		
		//Conversion to save the booking info in booking table
		BookingEntity bookingEntity = convertDtoToEntity(booking);
		BookingEntity newBooking = bookingDao.save(bookingEntity);
		return convertEntityToDto(newBooking);
	}

}