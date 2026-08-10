package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record TeamView(String id, String name, String shortName, String logoUrl) implements Serializable {
}
