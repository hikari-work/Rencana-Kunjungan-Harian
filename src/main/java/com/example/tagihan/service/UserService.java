package com.example.tagihan.service;

import com.example.tagihan.entity.Roles;
import com.example.tagihan.entity.User;
import com.example.tagihan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public Mono<User> findByJid(String jid) {
		return userRepository.findById(jid);
	}
	public Mono<User> findByAccountOfficer(String accountOfficer) {
		return userRepository.findByAccountOfficer(accountOfficer);
	}

	public Mono<User> saveUser(String jid, String accountOfficer) {
		return userRepository.findById(jid)
				.switchIfEmpty(
						Mono.defer(() -> userRepository.save(new User(jid, accountOfficer, Roles.MEMBER)))
				);
	}
}