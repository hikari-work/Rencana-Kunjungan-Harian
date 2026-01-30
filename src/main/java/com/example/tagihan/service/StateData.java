package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateData {

	private State currentState;
	private Visit visit;
}