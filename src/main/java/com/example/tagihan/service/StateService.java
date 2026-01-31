package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Service
public class StateService {

	private final BillsService billsService;
	private final Map<String, StateData> state = new LinkedHashMap<>();

	public StateService(BillsService billsService) {
		this.billsService = billsService;
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

	public State setVisitData(String jid, Visit visitUpdate) {
		if (!isUserInState(jid)) {
			log.warn("User {} not in state", jid);
			return null;
		}

		StateData stateData = getUserState(jid);
		Visit visit = stateData.getVisit();
		VisitType visitType = visit.getVisitType();

		if (visit.getSpk() == null && visitUpdate.getSpk() != null) {
			billsService.findBillBySpk(visitUpdate.getSpk())
					.subscribe(
							bills -> {
								visit.setSpk(visitUpdate.getSpk());
								visit.setDebitTray(bills.getDebitTray());
								visit.setInterest(bills.getLastInterest());
								visit.setPrincipal(bills.getPrincipal());
								visit.setPenalty(bills.getPenaltyInterest() + bills.getPenaltyPrincipal());
								visit.setPlafond(bills.getPlafond());
								log.info("Bills data fetched for SPK: {}", visitUpdate.getSpk());
							},
							err -> log.error("Error fetching bills for SPK {}: {}", visitUpdate.getSpk(), err.getMessage())
					);

			if (visit.getSpk() == null) {
				setState(jid, State.ADD_SPK);
				return State.ADD_SPK;
			}
		}

		if (visit.getVisitType() == null && visitUpdate.getVisitType() != null) {
			visit.setVisitType(visitUpdate.getVisitType());
		}

		if (visit.getVisitDate() == null && visitUpdate.getVisitDate() != null) {
			visit.setVisitDate(visitUpdate.getVisitDate());
		}

		if (visit.getNote() == null) {
			if (visitUpdate.getNote() != null) {
				visit.setNote(visitUpdate.getNote());
			} else {
				setState(jid, State.ADD_CAPTION);
				return State.ADD_CAPTION;
			}
		}

		if (visit.getImageUrl() == null && visitUpdate.getImageUrl() != null) {
			visit.setImageUrl(visitUpdate.getImageUrl());
		}

		if (visitType == VisitType.MONITORING || visitType == VisitType.TAGIHAN) {
			if (visit.getAppointment() == null) {
				if (visitUpdate.getAppointment() != null) {
					visit.setAppointment(visitUpdate.getAppointment());
				} else {
					setState(jid, State.ADD_APPOINTMENT);
					return State.ADD_APPOINTMENT;
				}
			}
		}

		if (visitType == VisitType.CANVASING || visitType == VisitType.SURVEY) {
			if (visit.getPlafond() == null && visitUpdate.getPlafond() != null) {
				visit.setPlafond(visitUpdate.getPlafond());
			}
		}

		return stateData.getCurrentState();
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
            default -> state.name();
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
			default -> false;
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