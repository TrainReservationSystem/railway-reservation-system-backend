package com.app.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.app.dao.CanceledTrainDao;
import com.app.dao.RouteDAO;
import com.app.dao.TrainDAO;
import com.app.dto.AddTrainDTO;
import com.app.dto.SearchTrainDTO;
import com.app.dto.SeatAvailabilityDTO;
import com.app.entities.CanceledTrainEntity;
import com.app.entities.RouteEntity;
import com.app.entities.TrainEntity;
import com.app.exceptions.ResourceNotFoundException;

@Service
@Transactional
public class TrainService {

	@Autowired
	private TrainDAO trainDAO;

	@Autowired
	private RouteService routeService;

	@Autowired
	private RouteDAO routeDao;

	@Autowired
	private ModelMapper modelMapper; // Autowire ModelMapper

	@Autowired
	private CanceledTrainDao canceledTrainDao;

	public List<AddTrainDTO> getAllTrains() {
		return trainDAO.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	public AddTrainDTO getTrainById(Long trainNumber) {
		TrainEntity trainEntity = trainDAO.findById((long) trainNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Train not found"));
		return convertToDTO(trainEntity);
	}

	public AddTrainDTO addTrain(AddTrainDTO trainDTO) {
		TrainEntity trainEntity = convertToEntity(trainDTO);
//        trainEntity.setRoute(routeService.getRouteById(trainDTO.getRouteId()).toEntity());
		trainEntity = trainDAO.save(trainEntity);
		return convertToDTO(trainEntity);
	}

	public void cancelTrain(Long trainNumber) {
		TrainEntity trainEntity = trainDAO.findById((long) trainNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Train not found"));
		trainEntity.setCancelStatus(true);
		trainEntity.setActiveStatus(false);
		trainDAO.save(trainEntity);
	}

	public void removeTrain(Long trainNumber) {
		TrainEntity trainEntity = trainDAO.findById((long) trainNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Train not found"));
		trainEntity.setCancelStatus(true);
		trainEntity.setActiveStatus(false);
		trainDAO.save(trainEntity);
	}
	
	public void activateTrain(Long trainNumber) {
        TrainEntity trainEntity = trainDAO.findById(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        trainEntity.setCancelStatus(false);
        trainEntity.setActiveStatus(true);
        trainDAO.save(trainEntity);
    }

	// Method to toggle the status of a train when it starts running
	public void toggleTrainStatusWhenRunning() {
		// Get all trains from the database
		List<TrainEntity> allTrains = trainDAO.findAll();

		// Iterate over each train
		for (TrainEntity train : allTrains) {
			// Check if the train is canceled and inactive
			if (train.isCancelStatus() && !train.isActiveStatus()) {
				// Get the current day of the week
				DayOfWeek currentDayOfWeek = LocalDate.now().getDayOfWeek();

				// Split the runsOn string into individual days
				String[] runDays = train.getRunsOn().split(",");

				// Check if the current day is in the list of run days
				for (String runDay : runDays) {
					if (getDayOfWeekFromString(runDay) == currentDayOfWeek) {
						// Toggle the cancelStatus to false and activeStatus to true
						train.setCancelStatus(false);
						train.setActiveStatus(true);

						// Save the updated train entity
						trainDAO.save(train);
						break; // Exit the loop once status is updated
					}
				}
			}
		}
	}

//	// Service method to cancel a train and reschedule it to a particular day of the week
//	public void cancelTrainAndReschedule(Long trainNumber, String rescheduleDay) {
//	    TrainEntity trainEntity = trainDAO.findById(trainNumber)
//	            .orElseThrow(() -> new ResourceNotFoundException("Train not found"));
//
//	    // Set cancelStatus and activeStatus
//	    trainEntity.setCancelStatus(true);
//	    trainEntity.setActiveStatus(false);
//
//	    // Reschedule the train to the provided day of the week
//	    trainEntity.setRunsOn(rescheduleDay);
//
//	    // Save the updated trainEntity
//	    trainDAO.save(trainEntity);
//	}

//	public void cancelTrainAndReschedule(Long trainNumber, LocalDate cancelDate) {
//		TrainEntity trainEntity = trainDAO.findById(trainNumber)
//				.orElseThrow(() -> new ResourceNotFoundException("Train not found"));
//
//		// Calculate the next running day based on the cancelDate
//		String nextRunningDay = calculateNextRunningDay(trainEntity.getRunsOn(), cancelDate);
//
//		// Remove the canceled day from runsOn
//		String updatedRunsOn = removeDayFromRunsOn(trainEntity.getRunsOn(), cancelDate.getDayOfWeek().toString());
//
//		// Set cancelStatus to true and activeStatus to false
//		trainEntity.setCancelStatus(true);
//		trainEntity.setActiveStatus(false);
//
//		// Set the updated runsOn
//		trainEntity.setRunsOn(updatedRunsOn);
//
//		// Save the updated trainEntity
//		trainDAO.save(trainEntity);
//
//		// Reschedule the train to the next running day
//		if (nextRunningDay != null && !nextRunningDay.isEmpty() && trainEntity.getRunsOn().contains(nextRunningDay)) {
//			trainEntity.setCancelStatus(false);
//			trainEntity.setActiveStatus(true);
//			trainEntity.setRunsOn(nextRunningDay);
//			trainDAO.save(trainEntity);
//		}
//	}
	private boolean runsOnContainsDay(String runsOn, String day) {
		String[] runDays = runsOn.split(",");
		for (String runDay : runDays) {
			if (runDay.trim().equalsIgnoreCase(day)) {
				return true;
			}
		}
		return false;
	}

	public void cancelTrainAndReschedule(Long trainNumber, LocalDate cancelDate) {
		TrainEntity trainEntity = trainDAO.findById(trainNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Train not found"));

		// Check if the cancellation date falls within the range of dates when the train
		// is scheduled to run
		if (!isCancellationDateValid(trainEntity, cancelDate)) {
			throw new IllegalArgumentException("Cannot cancel the train on a day when it's not scheduled to run");
		}

		// Cancel the train and update its status
		trainEntity.setCancelStatus(true);
		trainEntity.setActiveStatus(false);
		trainDAO.save(trainEntity);

		// Store the canceled train information in the CanceledTrainEntity table
		CanceledTrainEntity canceledTrain = new CanceledTrainEntity();
		canceledTrain.setTrainNumber(trainNumber);
		canceledTrain.setCancelDate(cancelDate);
		canceledTrainDao.save(canceledTrain);

		// Reschedule the train to the next running day if applicable
		String nextRunningDay = calculateNextRunningDay(trainEntity.getRunsOn(), cancelDate);
		if (nextRunningDay != null && !nextRunningDay.isEmpty() && trainEntity.getRunsOn().contains(nextRunningDay)) {
			trainEntity.setCancelStatus(false);
			trainEntity.setActiveStatus(true);
			trainEntity.setRunsOn(nextRunningDay);
			trainDAO.save(trainEntity);
		}
	}

	private boolean isCancellationDateValid(TrainEntity trainEntity, LocalDate cancelDate) {
		// Get the days when the train is scheduled to run
		String[] runDays = trainEntity.getRunsOn().split(",");

		// Get the day of the week for the cancellation date
		String cancelDayOfWeek = cancelDate.getDayOfWeek().toString().substring(0, 3);

		// Check if the cancellation date is among the days when the train is scheduled
		// to run
		for (String runDay : runDays) {
			if (runDay.equalsIgnoreCase(cancelDayOfWeek)) {
				return true;
			}
		}

		return false;
	}

//	// Service method to schedule a train after journey completion
//	public void scheduleTrainAfterJourneyCompletion(Long trainNumber, String runsOn) {
//	    // Calculate the next running day based on runsOn and current day
//	    String nextRunningDay = calculateNextRunningDay(runsOn);
//
//	    TrainEntity trainEntity = trainDAO.findById(trainNumber)
//	            .orElseThrow(() -> new ResourceNotFoundException("Train not found"));
//
//	    // Update the runsOn field of the train
//	    trainEntity.setRunsOn(nextRunningDay);
//
//	    // Set cancelStatus to false and activeStatus to true
//	    trainEntity.setCancelStatus(false);
//	    trainEntity.setActiveStatus(true);
//
//	    // Save the updated trainEntity
//	    trainDAO.save(trainEntity);
//	}

//	// Method to calculate the next running day based on runsOn field
//	private String calculateNextRunningDay(String runsOn) {
//	    // Get the current day of the week
//	    DayOfWeek currentDayOfWeek = LocalDate.now().getDayOfWeek();
//
//	    // Split the runsOn string into individual days
//	    String[] runDays = runsOn.split(",");
//
//	    // Find the next running day after the current day
//	    for (String runDay : runDays) {
//	        DayOfWeek nextDay = getDayOfWeekFromString(runDay);
//	        if (nextDay.compareTo(currentDayOfWeek) > 0) {
//	            return nextDay.toString();
//	        }
//	    }
//
//	    // If no future running day found, return the first day of the week
//	    return getDayOfWeekFromString(runDays[0]).toString();
//	}

	// Method to calculate the next running day based on runsOn and cancelDate
	private String calculateNextRunningDay(String runsOn, LocalDate cancelDate) {
		// Get the current day of the week
		DayOfWeek cancelDayOfWeek = cancelDate.getDayOfWeek();

		// Split the runsOn string into individual days
		String[] runDays = runsOn.split(",");

		// Find the next running day after the canceled day
		for (String runDay : runDays) {
			DayOfWeek nextDay = getDayOfWeekFromString(runDay);
			if (nextDay.compareTo(cancelDayOfWeek) > 0) {
				return nextDay.toString();
			}
		}

		// If no future running day found, return the first day of the week
		return getDayOfWeekFromString(runDays[0]).toString();
	}

	// Method to remove a specific day from runsOn
	private String removeDayFromRunsOn(String runsOn, String dayToRemove) {
		// Split the runsOn string into individual days
		String[] runDays = runsOn.split(",");

		// Create a list to store remaining days
		List<String> remainingDays = new ArrayList<>();

		// Add days to the remainingDays list except the one to be removed
		for (String runDay : runDays) {
			if (!runDay.equalsIgnoreCase(dayToRemove)) {
				remainingDays.add(runDay);
			}
		}

		// Join the remaining days into a comma-separated string
		return String.join(",", remainingDays);
	}

	// Helper method to convert day name string to DayOfWeek enum
	private DayOfWeek getDayOfWeekFromString(String day) {
		switch (day.toLowerCase()) {
		case "mon":
			return DayOfWeek.MONDAY;
		case "tue":
			return DayOfWeek.TUESDAY;
		case "wed":
			return DayOfWeek.WEDNESDAY;
		case "thu":
			return DayOfWeek.THURSDAY;
		case "fri":
			return DayOfWeek.FRIDAY;
		case "sat":
			return DayOfWeek.SATURDAY;
		case "sun":
			return DayOfWeek.SUNDAY;
		default:
			throw new IllegalArgumentException("Invalid day of week: " + day);
		}
	}

	private AddTrainDTO convertToDTO(TrainEntity trainEntity) {
		AddTrainDTO trainDTO = new AddTrainDTO();
		trainDTO.setTrainNumber(trainEntity.getTrainNumber());
		trainDTO.setTrainName(trainEntity.getTrainName());
		trainDTO.setArrivalTime(trainEntity.getArrivalTime());
		trainDTO.setDepartureTime(trainEntity.getDepartureTime());
//        trainDTO.setRunningDate(trainEntity.getRunningDate());
		trainDTO.setBaseFare(trainEntity.getBaseFare());
		trainDTO.setActiveStatus(trainEntity.isActiveStatus());
		trainDTO.setCancelStatus(trainEntity.isCancelStatus());
		trainDTO.setRouteId(trainEntity.getRoute().getRouteId());
		trainDTO.setRunsOn(trainEntity.getRunsOn());
		trainDTO.setAcSeats(trainEntity.getAcSeats());
		trainDTO.setSleeperSeats(trainEntity.getSleeperSeats());
		trainDTO.setGeneralSeats(trainEntity.getGeneralSeats());
//        trainDTO.setScheduleLink(trainEntity.getScheduleLink());
//        trainDTO.setDepartureStation(trainEntity.getDepartureStation());
//        trainDTO.setArrivalStation(trainEntity.getArrivalStation());
//        trainDTO.setDuration(trainEntity.getDuration());

		// Set the coachDTO
//        if (trainEntity.getCoach() != null) {
//            CoachDTO coachDTO = new CoachDTO();
//            coachDTO.setCoachType(trainEntity.getCoach().getCoachType().toString());
//            trainDTO.setCoachDTO(coachDTO);
//        }

		return trainDTO;
	}

	private TrainEntity convertToEntity(AddTrainDTO trainDTO) {
		TrainEntity trainEntity = new TrainEntity();
//        trainEntity.setTrainNumber(trainDTO.getTrainNumber());
		trainEntity.setTrainName(trainDTO.getTrainName());
		trainEntity.setArrivalTime(trainDTO.getArrivalTime());
		trainEntity.setDepartureTime(trainDTO.getDepartureTime());
//        trainEntity.setRunningDate(trainDTO.getRunningDate());
		trainEntity.setBaseFare(trainDTO.getBaseFare());
		trainEntity.setActiveStatus(trainDTO.isActiveStatus());
		trainEntity.setCancelStatus(trainDTO.isCancelStatus());
		trainEntity.setRunsOn(trainDTO.getRunsOn());
		trainEntity.setRoute(routeDao.findById(trainDTO.getRouteId())
				.orElseThrow(() -> new ResourceNotFoundException("Route Not Found")));
		trainEntity.setAcSeats(trainDTO.getAcSeats());
		trainEntity.setSleeperSeats(trainDTO.getSleeperSeats());
		trainEntity.setGeneralSeats(trainDTO.getGeneralSeats());
//        trainEntity.setScheduleLink(trainDTO.getScheduleLink());
//        trainEntity.setDepartureStation(trainDTO.getDepartureStation());
//        trainEntity.setArrivalStation(trainDTO.getArrivalStation());
//        trainEntity.setDuration(trainDTO.getDuration());

		// Set the coach
//        if (trainDTO.getCoachDTO() != null && trainDTO.getCoachDTO().getCoachType() != null) {
//            trainEntity.setCoachType(Coaches.valueOf(trainDTO.getCoachDTO().getCoachType()));
//        }

		return trainEntity;
	}

//	public List<SearchTrainDTO> getTrainsBySrcDescDate(String src, String des, LocalDate dateOfJourney) {
//		String day = dateOfJourney.getDayOfWeek().toString().substring(0, 3);
//
//		RouteEntity route = routeDao.findBySourceAndDestination(src, des)
//				.orElseThrow(() -> new ResourceNotFoundException("Route not found"));
//
//		List<TrainEntity> trainEntities = trainDAO.findByRouteIdAndRunsOnDay(route.getRouteId(), day);
//
//		List<SearchTrainDTO> searchTrains = new ArrayList<>();
//
//		if (!trainEntities.isEmpty()) {
//			for (TrainEntity trainEntity : trainEntities) {
//				SearchTrainDTO searchTrain = modelMapper.map(trainEntity, SearchTrainDTO.class); // Utilize ModelMapper
//				searchTrain.setSource(src);
//				searchTrain.setDestination(des);
//				searchTrain.setDateOfJourney(dateOfJourney.toString());
//
//				Optional<Object[]> seatsOptional = trainDAO
//						.findCoachCountsByTrainNumberAndDateOfJourney(trainEntity.getTrainNumber(), dateOfJourney);
//
//				if (seatsOptional.isPresent()) {
//					Object[] seats = seatsOptional.get();
//					if (seats.length > 0) {
//						SeatAvailabilityDTO seatAvailabilityDTO = new SeatAvailabilityDTO();
//						seatAvailabilityDTO.setTrainNumber((Long) seats[0]);
//						seatAvailabilityDTO.setDateOfJourney((LocalDate) seats[1]);
//						seatAvailabilityDTO.setAcCount((Integer) seats[2]);
//						seatAvailabilityDTO.setSleeperCount((Integer) seats[3]);
//						seatAvailabilityDTO.setGeneralCount((Integer) seats[4]);
//
//						searchTrain.setAcSeats(trainEntity.getAcSeats() - seatAvailabilityDTO.getAcCount());
//						searchTrain
//								.setSleeperSeats(trainEntity.getSleeperSeats() - seatAvailabilityDTO.getSleeperCount());
//						searchTrain
//								.setGeneralSeats(trainEntity.getGeneralSeats() - seatAvailabilityDTO.getGeneralCount());
//					} else {
//						// Handle no seat availability data
//					}
//				} else {
//					// Handle no seat availability data
//				}
//
//				searchTrains.add(searchTrain);
//			}
//		} else {
//			// Handle no trains found
//		}
//
//		return searchTrains;
//	}

	public List<SearchTrainDTO> getTrainsBySrcDescDate(String src, String des, LocalDate dateOfJourney) {
//		String day = dateOfJourney.getDayOfWeek().toString().substring(0, 3);
//		System.out.println(day);
		String day = dateOfJourney.getDayOfWeek().toString().substring(0, 3);
		day = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
//		System.out.println(day);

		RouteEntity route = routeDao.findBySourceAndDestination(src, des)
				.orElseThrow(() -> new ResourceNotFoundException("Route not found"));

		List<TrainEntity> trainEntities = trainDAO.findByRouteIdAndRunsOnDay(route.getRouteId(), day);

		List<SearchTrainDTO> searchTrains = new ArrayList<>();

		if (!trainEntities.isEmpty()) {
			for (TrainEntity trainEntity : trainEntities) {
				// Check if the train is canceled for the given date
				if (isTrainCanceled(trainEntity.getTrainNumber(), dateOfJourney)) {
					continue; // Skip this train if it is canceled
				}

				SearchTrainDTO searchTrain = modelMapper.map(trainEntity, SearchTrainDTO.class); // Utilize ModelMapper
				searchTrain.setSource(src);
				searchTrain.setDestination(des);
				searchTrain.setDateOfJourney(dateOfJourney.toString());

				// Populate seat availability details
				Optional<Object[]> seatsOptional = trainDAO
						.findCoachCountsByTrainNumberAndDateOfJourney(trainEntity.getTrainNumber(), dateOfJourney);
				if (seatsOptional.isPresent()) {
					Object[] seats = seatsOptional.get();
					if (seats.length > 0) {
						SeatAvailabilityDTO seatAvailabilityDTO = new SeatAvailabilityDTO();
						seatAvailabilityDTO.setTrainNumber(trainEntity.getTrainNumber());
						seatAvailabilityDTO.setDateOfJourney(dateOfJourney);
						seatAvailabilityDTO.setAcCount((Integer) seats[2]);
						seatAvailabilityDTO.setSleeperCount((Integer) seats[3]);
						seatAvailabilityDTO.setGeneralCount((Integer) seats[4]);

						// Set seat availability details directly to the SearchTrainDTO
						searchTrain.setAcSeats(trainEntity.getAcSeats() - seatAvailabilityDTO.getAcCount());
						searchTrain
								.setSleeperSeats(trainEntity.getSleeperSeats() - seatAvailabilityDTO.getSleeperCount());
						searchTrain
								.setGeneralSeats(trainEntity.getGeneralSeats() - seatAvailabilityDTO.getGeneralCount());
					} else {
						// Handle no seat availability data
					}
				} else {
					// Handle no seat availability data
				}

				searchTrains.add(searchTrain);
			}
		} else {
			// Handle no trains found
		}

		return searchTrains;
	}

	private boolean isTrainCanceled(Long trainNumber, LocalDate dateOfJourney) {
		// Query the canceled trains entity to check if the train with the given train
		// number is canceled for the provided date
		Optional<CanceledTrainEntity> canceledTrain = canceledTrainDao.findByTrainNumberAndCancelDate(trainNumber,
				dateOfJourney);
		return canceledTrain.isPresent();
	}

}
