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
	private final UserService userService;

	public StateService(BillsService billsService, ApplicationEventPublisher publisher, UserService userService) {
		this.billsService = billsService;
		this.publisher = publisher;
		this.userService = userService;
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

									return saveUserState(jid, stateData)
											.then(processVisitData(jid, stateData, visit, visitUpdate));
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
			StateData existingState = getUserState(jid);
			if (existingState != null) {
				log.debug("Found existing state for user: {}", jid);
				return Mono.just(existingState);
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

			log.info("Created new state for user: {}", jid);

			return saveUserState(jid, newStateData)
					.thenReturn(newStateData);
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

		if (visitUpdate.getReminderDate() != null && visit.getReminderDate() == null) {
			visit.setReminderDate(visitUpdate.getReminderDate());
		}

		if (visitUpdate.getName() != null && visit.getName() == null) {
			visit.setName(visitUpdate.getName());
		}

		if (visitUpdate.getAddress() != null && visit.getAddress() == null) {
			visit.setAddress(visitUpdate.getAddress());
		}

		if (visitUpdate.getInterested() != null && visit.getInterested() == null) {
			visit.setInterested(visitUpdate.getInterested());
		}

		return determineNextState(visit)
				.flatMap(nextState -> Mono.fromRunnable(() -> {
                            setState(jid, nextState);
                            stateData.setCurrentState(nextState);
                        })
                        .then(saveUserState(jid, stateData))
                        .doOnSuccess(saved -> log.info("Next state for user {}: {}", jid, nextState))
                        .thenReturn(nextState));
	}

	private Mono<State> determineNextState(Visit visit) {
		return userService.findByJid(visit.getUserId())
				.mapNotNull(user -> determineStateFromVisit(visit))
				.switchIfEmpty(
						Mono.just(State.REGISTER)
				);
	}

	private State determineStateFromVisit(Visit visit) {
		VisitType visitType = visit.getVisitType();
		if (visitType.equals(VisitType.INFORMATIONAL)) {
			if (visit.getSpk() == null) {
				return State.ADD_SPK;
			}
			if (visit.getAppointment() == null) {
				return State.ADD_APPOINTMENT;
			}
			if (visit.getReminderDate() == null) {
				return State.ADD_REMINDER;
			}
		}


		if (requiresSpk(visitType) && visit.getSpk() == null) {
			return State.ADD_SPK;
		}

		if (requiresCaption(visitType) && visit.getNote() == null) {
			return State.ADD_CAPTION;
		}

		if (requiresLimit(visitType) && visit.getPlafond() == null) {
			return State.ADD_LIMIT;
		}

		if (requiresAppointment(visitType) && visit.getAppointment() == null) {
			return State.ADD_APPOINTMENT;
		}
		if (visit.getAppointment() != null) {
			if (requiresReminder(visitType) && visit.getReminderDate() == null) {
				return State.ADD_REMINDER;
			}
		}
		if (visit.getName() == null) {
			return State.ADD_NAME;
		}

		if (requiresAddress(visitType) && visit.getAddress() == null) {
			return State.ADD_ADDRESS;
		}
		if (visit.getUsaha() == null) {
			return State.ADD_USAHA;
		}


		return State.COMPLETED;
	}

	private boolean requiresSpk(VisitType visitType) {
		return visitType == VisitType.MONITORING || visitType == VisitType.TAGIHAN;
	}

	/**
	 * Caption diperlukan untuk: MONITORING, TAGIHAN, CANVASING (tidak untuk SURVEY)
	 */
	private boolean requiresCaption(VisitType visitType) {
		return visitType != VisitType.SURVEY && visitType != VisitType.INFORMATIONAL;
	}

	/**
	 * Reminder diperlukan untuk: TAGIHAN
	 */
	private boolean requiresReminder(VisitType visitType) {
		return visitType == VisitType.TAGIHAN;
	}

	/**
	 * Limit diperlukan untuk: SURVEY
	 */
	private boolean requiresLimit(VisitType visitType) {
		return visitType == VisitType.SURVEY;
	}

	/**
	 * Appointment diperlukan untuk: TAGIHAN
	 */
	private boolean requiresAppointment(VisitType visitType) {
		return visitType == VisitType.TAGIHAN;
	}

	/**
	 * Address diperlukan untuk: CANVASING
	 */
	private boolean requiresAddress(VisitType visitType) {
		return visitType == VisitType.CANVASING;
	}

	private Mono<Void> saveUserState(String jid, StateData stateData) {
		return Mono.fromRunnable(() -> state.put(jid, stateData))
				.then();
	}

	public void removeState(String jid) {
		StateData removed = state.remove(jid);
		if (removed != null) {
			log.info("State removed for JID: {}", jid);
		}
	}

}