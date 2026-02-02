package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.event.StateChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Service
public class StateService {
	private final ApplicationEventPublisher publisher;

	private final BillsService billsService;
	private final Map<String, StateData> state = new LinkedHashMap<>();

	public StateService(BillsService billsService, ApplicationEventPublisher publisher) {
		this.billsService = billsService;
		this.publisher = publisher;
	}

	public boolean isUserInState(String jid) {
		return state.containsKey(jid);
	}

	public StateData getUserState(String jid) {
		return state.get(jid);
	}


	public State getCurrentState(String jid) {
		StateData stateData = getUserState(jid);
		return stateData != null ? stateData.getCurrentState() : null;
	}


	public VisitType getVisitType(String jid) {
		StateData stateData = getUserState(jid);
		return stateData != null && stateData.getVisit() != null
				? stateData.getVisit().getVisitType()
				: null;
	}


	public Visit getVisit(String jid) {
		StateData stateData = getUserState(jid);
		return stateData != null ? stateData.getVisit() : null;
	}


	public void setState(String jid, State newState) {
		if (isUserInState(jid)) {
			state.get(jid).setCurrentState(newState);
			publisher.publishEvent(new StateChangedEvent(this, state.get(jid)));
			log.info("State updated for JID: {}, new State: {}", jid, newState);
		}
	}

	public StateData createState(String jid, Visit visit, State initialState) {
		if (isUserInState(jid)) {
			StateData existingState = getUserState(jid);
			log.warn("State creation REJECTED for JID: {}. User already has active state: {}",
					jid, existingState.getCurrentState());
			throw new IllegalStateException(
					String.format("Anda sudah memiliki proses aktif: %s. Ketik 'batal' untuk membatalkan.",
							getStateName(existingState.getCurrentState()))
			);
		}

		StateData stateData = StateData.builder()
				.currentState(initialState)
				.visit(visit)
				.build();

		state.put(jid, stateData);
		log.info("New state CREATED for JID: {}, State: {}", jid, initialState);
		return stateData;
	}

	public void updateState(String jid, StateData stateData) {
		state.put(jid, stateData);
		log.info("State updated for JID: {}", jid);
	}

	public Mono<State> setVisitData(String jid, Visit visitUpdate) {
		return getOrCreateStateData(jid, visitUpdate)
				.flatMap(stateData -> {
					Visit visit = stateData.getVisit();

					if (visit.getSpk() == null && visitUpdate.getSpk() != null) {
						return billsService.findBillBySpk(visitUpdate.getSpk())
								.flatMap(bills -> {
									visit.setSpk(visitUpdate.getSpk());
									visit.setDebitTray(bills.getDebitTray());
									visit.setInterest(bills.getLastInterest());
									visit.setPrincipal(bills.getPrincipal());
									visit.setPenalty(bills.getPenaltyInterest() + bills.getPenaltyPrincipal());
									visit.setPlafond(bills.getPlafond());
									visit.setName(bills.getName());
									visit.setAddress(bills.getAddress());
									log.info("Bills data fetched for SPK: {}", visitUpdate.getSpk());

									saveUserState(jid, stateData);

									return processVisitData(jid, stateData, visit, visitUpdate);
								})
								.switchIfEmpty(
										Mono.error(new IllegalStateException("SPK tidak ditemukan"))
								);
					}

					return processVisitData(jid, stateData, visit, visitUpdate);
				});
	}

	private Mono<StateData> getOrCreateStateData(String jid, Visit visitUpdate) {
		return Mono.defer(() -> {
			try {
				StateData existingState = getUserState(jid);
				if (existingState != null) {
					log.debug("Found existing state for user: {}", jid);
					return Mono.just(existingState);
				}
			} catch (Exception e) {
				log.info("No existing state found for user: {}, creating new state", jid);
			}

			Visit newVisit = Visit.builder()
					.userId(jid)
					.spk(visitUpdate.getSpk())
					.name(visitUpdate.getName())
					.note(visitUpdate.getNote())
					.address(visitUpdate.getAddress())
					.appointment(visitUpdate.getAppointment())
					.reminderDate(visitUpdate.getReminderDate())
					.debitTray(visitUpdate.getDebitTray())
					.penalty(visitUpdate.getPenalty())
					.interest(visitUpdate.getInterest())
					.principal(visitUpdate.getPrincipal())
					.plafond(visitUpdate.getPlafond())
					.visitType(visitUpdate.getVisitType())
					.visitDate(visitUpdate.getVisitDate())
					.imageUrl(visitUpdate.getImageUrl())
					.build();

			StateData newStateData = StateData.builder()
					.currentState(State.ADD_SPK)
					.visit(newVisit)
					.build();

			saveUserState(jid, newStateData);
			log.info("Created new state for user: {}", jid);

			return Mono.just(newStateData);
		});
	}

	private Mono<State> processVisitData(String jid, StateData stateData, Visit visit, Visit visitUpdate) {
		if (visitUpdate.getVisitType() != null && visit.getVisitType() == null) {
			visit.setVisitType(visitUpdate.getVisitType());
		}

		if (visitUpdate.getVisitDate() != null && visit.getVisitDate() == null) {
			visit.setVisitDate(visitUpdate.getVisitDate());
		}

		if (visitUpdate.getNote() != null && visit.getNote() == null) {
			visit.setNote(visitUpdate.getNote());
		}

		if (visitUpdate.getImageUrl() != null && visit.getImageUrl() == null) {
			visit.setImageUrl(visitUpdate.getImageUrl());
		}

		if (visitUpdate.getAppointment() != null && visit.getAppointment() == null) {
			visit.setAppointment(visitUpdate.getAppointment());
		}

		if (visitUpdate.getPlafond() != null && visit.getPlafond() == null) {
			visit.setPlafond(visitUpdate.getPlafond());
		}

		// Tentukan state berikutnya berdasarkan data yang masih kurang
		State nextState = determineNextState(visit);

		// Update state
		setState(jid, nextState);
		stateData.setCurrentState(nextState);

		// Simpan perubahan
		saveUserState(jid, stateData);

		log.info("Next state for user {}: {}", jid, nextState);

		return Mono.just(nextState);
	}

	private State determineNextState(Visit visit) {
		// 1. Cek SPK (paling pertama)
		if (visit.getSpk() == null) {
			return State.ADD_SPK;
		}

		// 2. Cek Note/Caption
		if (visit.getNote() == null) {
			return State.ADD_CAPTION;
		}

		VisitType visitType = visit.getVisitType();

		// 3. Cek Appointment untuk MONITORING atau TAGIHAN
		if (visitType == VisitType.MONITORING || visitType == VisitType.TAGIHAN) {
			if (visit.getAppointment() == null) {
				return State.ADD_APPOINTMENT;
			}
		}

		// 4. Cek Plafond untuk CANVASING atau SURVEY
		if (visitType == VisitType.CANVASING || visitType == VisitType.SURVEY) {
			if (visit.getPlafond() == null) {
				return State.ADD_LIMIT; // Assuming you have this state
			}
		}


		return null;
	}

	// Helper method untuk save state (implement sesuai repository Anda)
	private void saveUserState(String jid, StateData stateData) {
		state.put(jid, stateData);
	}

	public boolean removeState(String jid) {
		StateData removed = state.remove(jid);
		if (removed != null) {
			log.info("State removed for JID: {}", jid);
			return true;
		}
		return false;
	}


	public void clearAllStates() {
		int count = state.size();
		state.clear();
		log.warn("All states cleared. Total: {}", count);
	}

	public int getTotalUsers() {
		return state.size();
	}


	public String getStateName(State state) {
		if (state == null) return "Unknown";

        return switch (state) {
            case REGISTER -> "Registrasi";
            case ADD_SPK -> "Tambah SPK";
            case ADD_CAPTION -> "Tambah Catatan";
            case ADD_REMINDER -> "Tambah Reminder";
            case ADD_LIMIT -> "Tambah Limit";
            case ADD_APPOINTMENT -> "Tambah Appointment";
        };
	}


	public boolean isVisitComplete(String jid) {
		if (!isUserInState(jid)) {
			return false;
		}

		Visit visit = getVisit(jid);
		if (visit == null) {
			return false;
		}

		VisitType visitType = visit.getVisitType();

		if (visit.getUserId() == null || visit.getVisitType() == null ||
				visit.getVisitDate() == null || visit.getNote() == null) {
			return false;
		}

		return switch (visitType) {
			case TAGIHAN, MONITORING ->
					visit.getAppointment() != null;
			case CANVASING, SURVEY ->
					visit.getPlafond() != null;
        };
	}

	public Map<String, StateData> getAllStates() {
		return new LinkedHashMap<>(state);
	}


	public void printAllStates() {
		log.info("=== Current States ===");
		log.info("Total Users: {}", getTotalUsers());

		state.forEach((jid, stateData) -> log.info("JID: {}, State: {}, VisitType: {}",
				jid,
				stateData.getCurrentState(),
				stateData.getVisit() != null ? stateData.getVisit().getVisitType() : "NULL"));
		log.info("===================");
	}
}